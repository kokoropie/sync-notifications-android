package com.kokoropie.notification

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Android 10+ chặn app nền đọc clipboard, nhưng Accessibility Service được phép mở activity từ nền.
 * Khi clipboard đổi, mở ClipSendActivity (trong suốt, có focus) để đọc và gửi nội dung đi.
 * Service không đọc nội dung màn hình (canRetrieveWindowContent=false).
 */
class ClipboardAccessibilityService : AccessibilityService() {
  private lateinit var cm: ClipboardManager
  private val listener = ClipboardManager.OnPrimaryClipChangedListener {
    val p = Prefs.get(this)
    if (!p.isConfigured || !p.clipboardSync) return@OnPrimaryClipChangedListener
    startActivity(
      Intent(this, ClipSendActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        .putExtra("silent", true),
    )
  }

  override fun onServiceConnected() {
    cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.addPrimaryClipChangedListener(listener)
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
  override fun onInterrupt() {}

  override fun onDestroy() {
    if (::cm.isInitialized) cm.removePrimaryClipChangedListener(listener)
    super.onDestroy()
  }
}
