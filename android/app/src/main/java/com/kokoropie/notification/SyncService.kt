package com.kokoropie.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Foreground service giữ WebSocket tới server để đồng bộ clipboard hai chiều.
 * (Thông báo và cuộc gọi gửi qua HTTP độc lập, không phụ thuộc service này.)
 */
class SyncService : Service() {
  private val http = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
  private var ws: WebSocket? = null
  private var retry = 0
  private var stopped = false
  private val main = Handler(Looper.getMainLooper())
  private lateinit var clipboard: ClipboardManager
  private var lastClip: String? = null // nội dung vừa nhận từ remote / vừa gửi, để chống vòng lặp

  private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
    // Android 10+: chỉ đọc được khi app đang foreground; nếu null thì bỏ qua (dùng nút/tile/share để gửi tay)
    pushLocalClipboard()
  }

  override fun onCreate() {
    super.onCreate()
    clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.addPrimaryClipChangedListener(clipListener)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForegroundCompat()
    if (!Prefs.get(this).isConfigured || !Prefs.get(this).clipboardSync) {
      stopSelf()
      return START_NOT_STICKY
    }
    stopped = false
    connect()
    return START_STICKY
  }

  private fun startForegroundCompat() {
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL, "Đồng bộ clipboard", NotificationManager.IMPORTANCE_MIN))
    val n = Notification.Builder(this, CHANNEL)
      .setContentTitle("Đồng bộ clipboard với Mac")
      .setSmallIcon(R.drawable.ic_stat_bell)
      .setOngoing(true)
      .build()
    if (android.os.Build.VERSION.SDK_INT >= 34) {
      startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(1, n)
    }
  }

  private fun connect() {
    ws?.cancel()
    val p = Prefs.get(this)
    val q = "key=${enc(p.accountKey)}&deviceId=${enc(p.deviceId)}&name=${enc(p.deviceName)}&platform=android"
    val url = p.serverUrl.replaceFirst("http", "ws") + "/ws?" + q
    ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) { retry = 0 }

      override fun onMessage(webSocket: WebSocket, text: String) {
        val m = try { JSONObject(text) } catch (e: Exception) { return }
        if (m.optString("kind") == "clipboard") applyRemote(m.optString("text"))
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { scheduleReconnect(webSocket) }
      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { scheduleReconnect(webSocket) }
    })
  }

  private fun scheduleReconnect(from: WebSocket) {
    if (stopped || from !== ws) return
    retry++
    val delay = minOf(30_000L, 1000L shl minOf(retry, 5))
    main.postDelayed({ if (!stopped) connect() }, delay)
  }

  private fun applyRemote(text: String) {
    if (text.isEmpty()) return
    main.post {
      lastClip = text
      clipboard.setPrimaryClip(ClipData.newPlainText("mac", text))
    }
  }

  private fun pushLocalClipboard() {
    val text = try {
      clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
    } catch (e: Exception) { null }
    if (text.isNullOrEmpty() || text == lastClip) return
    lastClip = text
    ws?.send(JSONObject().put("kind", "clipboard").put("text", text).toString())
  }

  override fun onDestroy() {
    stopped = true
    clipboard.removePrimaryClipChangedListener(clipListener)
    ws?.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

  companion object {
    private const val CHANNEL = "sync"

    fun start(ctx: Context) {
      val p = Prefs.get(ctx)
      if (p.isConfigured && p.clipboardSync) ctx.startForegroundService(Intent(ctx, SyncService::class.java))
    }

    fun stop(ctx: Context) {
      ctx.stopService(Intent(ctx, SyncService::class.java))
    }
  }
}
