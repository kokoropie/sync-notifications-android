package com.kokoropie.notification

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** HTTP client tối giản; gửi sự kiện lên server kèm retry có backoff. */
object Api {
  private const val TAG = "NotifyApi"
  private val retryDelays = longArrayOf(5, 30, 120, 600) // giây
  private val pool = ScheduledThreadPoolExecutor(2)

  fun request(ctx: Context, method: String, path: String, body: JSONObject? = null): String {
    val p = Prefs.get(ctx)
    require(p.isConfigured) { "Chưa cấu hình server" }
    val conn = URL(p.serverUrl + path).openConnection() as HttpURLConnection
    try {
      conn.requestMethod = method
      conn.connectTimeout = 10_000
      conn.readTimeout = 15_000
      conn.setRequestProperty("Authorization", "Bearer ${p.accountKey}")
      conn.setRequestProperty("X-Device-Id", p.deviceId)
      if (body != null) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
      }
      val code = conn.responseCode
      val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
      if (code >= 400) throw java.io.IOException("HTTP $code $text")
      return text
    } finally {
      conn.disconnect()
    }
  }

  /** Gửi bất đồng bộ, tự retry khi mạng lỗi. */
  fun sendEvent(ctx: Context, payload: JSONObject, iconPackage: String? = null) {
    val app = ctx.applicationContext
    if (!Prefs.get(app).isConfigured) return
    attempt(app, "/api/events", payload, 0, iconPackage)
  }

  fun sendAsync(ctx: Context, path: String, payload: JSONObject) {
    val app = ctx.applicationContext
    if (!Prefs.get(app).isConfigured) return
    attempt(app, path, payload, 0)
  }

  private fun attempt(ctx: Context, path: String, payload: JSONObject, n: Int, iconPackage: String? = null) {
    pool.execute {
      try {
        if (iconPackage != null) uploadIconOnce(ctx, iconPackage) // icon lên trước để Mac tải được ngay khi nhận event
        request(ctx, "POST", path, payload)
      } catch (e: Exception) {
        Log.w(TAG, "send failed (attempt $n): ${e.message}")
        // 4xx (trừ lỗi mạng) là lỗi cố định, không retry
        val fatal = e.message?.startsWith("HTTP 4") == true
        if (!fatal && n < retryDelays.size) pool.schedule({ attempt(ctx, path, payload, n + 1, iconPackage) }, retryDelays[n], TimeUnit.SECONDS)
      }
    }
  }

  private fun uploadIconOnce(ctx: Context, pkg: String) {
    val p = Prefs.get(ctx)
    if (p.iconSent(pkg)) return
    val png = AppIcons.base64(ctx, pkg, 128) ?: return
    try {
      request(ctx, "PUT", "/api/icons/" + java.net.URLEncoder.encode(pkg, "UTF-8"), JSONObject().put("png", png))
      p.markIconSent(pkg)
    } catch (e: Exception) {
      // lỗi mạng: thôi, event vẫn gửi; lần sau thử lại. Lỗi mạng thật sẽ làm request kế tiếp fail và retry.
      Log.w(TAG, "icon upload failed: ${e.message}")
    }
  }

  fun io(block: () -> Unit) = Executors.newSingleThreadExecutor().execute(block)
}
