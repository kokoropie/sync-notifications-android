package com.kokoropie.notification

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.json.JSONObject

/**
 * Activity trong suốt để gửi clipboard sang Mac. Dùng cho:
 *  - Share sheet ("Gửi sang Mac"): nhận text từ ACTION_SEND
 *  - Quick Settings tile: đọc clipboard (Android 10+ chỉ cho đọc khi có cửa sổ focus)
 */
class ClipSendActivity : Activity() {
  private var done = false
  private val silent get() = intent?.getBooleanExtra("silent", false) == true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent?.action == Intent.ACTION_SEND) {
      send(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (!hasFocus || done) return
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    send(cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString())
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
