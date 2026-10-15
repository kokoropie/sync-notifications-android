package com.kokoropie.notification

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Android 10+ chặn app nền đọc clipboard, nhưng Accessibility Service được phép mở activity từ nền.
 * Khi clipboard đổi, mở ClipSendActivity (trong suốt, có focus) để đọc và gửi nội dung đi.
 * Service không đọc nội dung màn hình (canRetrieveWindowContent=false).
 *
 * Tắt/bật lại trợ năng: hệ thống tạo instance mới, instance cũ có thể chưa được onDestroy kịp
 * (hoặc không bao giờ) -> listener cũ còn treo trên ClipboardManager. Vì vậy listener được giữ ở
 * companion để luôn gỡ cái cũ trước khi đăng ký cái mới, và gỡ cả ở onUnbind.
 */
class ClipboardAccessibilityService : AccessibilityService() {
  override fun onServiceConnected() {
    super.onServiceConnected()
    register()
  }

  private fun register() {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    unregister(cm)
    val ctx = applicationContext
    val l = ClipboardManager.OnPrimaryClipChangedListener {
      val p = Prefs.get(ctx)
      if (!p.isConfigured || !p.clipboardSync) return@OnPrimaryClipChangedListener
      try {
        ctx.startActivity(
          Intent(ctx, ClipSendActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            .putExtra("silent", true),
        )
      } catch (e: Exception) {
        Log.w(TAG, "không mở được ClipSendActivity", e)
      }
    }
    cm.addPrimaryClipChangedListener(l)
    current = l
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
  override fun onInterrupt() {}

  override fun onUnbind(intent: Intent?): Boolean {
    unregister(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    unregister(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
    super.onDestroy()
  }

  private fun unregister(cm: ClipboardManager) {
    current?.let { cm.removePrimaryClipChangedListener(it) }
    current = null
  }

  companion object {
    private const val TAG = "ClipA11y"

    @Volatile
    private var current: ClipboardManager.OnPrimaryClipChangedListener? = null
  }
}
