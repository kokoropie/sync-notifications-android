package com.kokoropie.notification

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import org.json.JSONObject

/**
 * Activity trong suốt để gửi clipboard sang Mac. Dùng cho:
 *  - Share sheet ("Gửi sang Mac"): nhận text hoặc ảnh từ ACTION_SEND
 *  - Quick Settings tile: đọc clipboard (Android 10+ chỉ cho đọc khi có cửa sổ focus)
 */
class ClipSendActivity : Activity() {
  private var done = false
  private val silent get() = intent?.getBooleanExtra("silent", false) == true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent?.action == Intent.ACTION_SEND) {
      if (intent.type?.startsWith("image/") == true) {
        @Suppress("DEPRECATION")
        sendImage { (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { ClipImages.readUri(this, it) } }
      } else {
        send(intent.getStringExtra(Intent.EXTRA_TEXT))
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (!hasFocus || done) return
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip
    if (ClipImages.isImage(clip)) return sendImage { ClipImages.read(this, clip) }
    send(clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString())
  }

  /** Đọc + upload ở thread nền (quyền đọc URI của clipboard vẫn còn sau khi activity đóng). */
  private fun sendImage(read: () -> Pair<ByteArray, String>?) {
    if (done) return
    done = true
    if (!Prefs.get(this).isConfigured) {
      toast("Chưa cấu hình server")
    } else {
      val app = applicationContext
      Api.io {
        val (bytes, mime) = read() ?: return@io
        try { ClipImages.send(app, bytes, mime) } catch (e: Exception) { ClipImages.lastHash = null }
      }
      toast("Đang gửi ảnh sang Mac")
    }
    finish()
  }

  private fun send(text: String?) {
    if (done) return
    done = true
    when {
      !Prefs.get(this).isConfigured -> toast("Chưa cấu hình server")
      text.isNullOrEmpty() -> toast("Clipboard trống")
      else -> {
        Api.sendAsync(this, "/api/clipboard", JSONObject().put("text", text))
        toast("Đã gửi sang Mac")
      }
    }
    finish()
  }

  private fun toast(s: String) = if (silent) Unit else Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
