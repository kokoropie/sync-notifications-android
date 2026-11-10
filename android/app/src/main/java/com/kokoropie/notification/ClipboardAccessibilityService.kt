package com.kokoropie.notification

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Android 10+ chặn app nền đọc clipboard VÀ không gọi OnPrimaryClipChangedListener của app không có focus
 * (kể cả listener nằm trong Accessibility Service). Vì vậy phải tự đoán lúc người dùng copy qua sự kiện trợ năng:
 *  - bấm nút "Sao chép"/"Copy" (menu chọn chữ, nút copy trong app)
 *  - toast / thông báo "Đã sao chép", overlay clipboard của SystemUI (Android 13+)
 * rồi mở ClipSendActivity (trong suốt, có focus) để đọc và gửi nội dung đi.
 * Service không đọc nội dung màn hình (canRetrieveWindowContent=false), chỉ so chữ của sự kiện với từ khoá copy.
 *
 * Listener clipboard vẫn giữ cho trường hợp hệ thống/ROM có gọi (vd app đang foreground).
 * Tắt/bật lại trợ năng: hệ thống tạo instance mới, instance cũ có thể chưa được onDestroy kịp
 * (hoặc không bao giờ) -> listener cũ còn treo trên ClipboardManager. Vì vậy listener được giữ ở
 * companion để luôn gỡ cái cũ trước khi đăng ký cái mới, và gỡ cả ở onUnbind.
 */
class ClipboardAccessibilityService : AccessibilityService() {
  private val main = Handler(Looper.getMainLooper())
  private val launch = Runnable { openSender() }
  private val keywords by lazy { copyKeywords() }

  override fun onServiceConnected() {
    super.onServiceConnected()
    register()
  }

  private fun register() {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    unregister(cm)
    val l = ClipboardManager.OnPrimaryClipChangedListener { schedule() }
    cm.addPrimaryClipChangedListener(l)
    current = l
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    event ?: return
    if (event.packageName == packageName) return
    if (looksLikeCopy(event)) schedule()
  }

  private fun looksLikeCopy(e: AccessibilityEvent): Boolean {
    val texts = buildList {
      e.text?.forEach { it?.let(::add) }
      e.contentDescription?.let(::add)
    }
    // bấm: nhãn nút phải đúng là "Copy"/"Sao chép" (tránh bấm vào tin nhắn có chữ copy);
    // toast/thông báo/overlay hệ thống: phải là thể đã xong ("Đã sao chép", "Text copied"...). Không khớp "Copy"
    // ở đây vì thanh công cụ nổi khi bôi đen chữ ("Cut Copy Paste...") cũng bắn sự kiện cửa sổ -> mở activity
    // sẽ cướp focus và làm mất vùng bôi đen.
    val click = e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
    return texts.any { t ->
      val s = t.toString().trim().lowercase()
      when {
        s.isEmpty() || s.length > 80 -> false
        click -> s in keywords || COPY_ACTION_RE.containsMatchIn(s)
        else -> COPIED_RE.containsMatchIn(s)
      }
    }
  }

  /** Đợi app ghi xong clipboard rồi mới mở activity; gộp nhiều sự kiện liên tiếp thành một lần. */
  private fun schedule() {
    main.removeCallbacks(launch)
    main.postDelayed(launch, 400)
  }

  private fun openSender() {
    val p = Prefs.get(this)
    if (!p.isConfigured || !p.clipboardSync) return
    // clipboard vừa được đặt từ Mac -> overlay "Đã sao chép" của hệ thống, không gửi ngược lại
    if (SystemClock.elapsedRealtime() - SyncService.lastRemoteAt < 2000) return
    try {
      startActivity(
        Intent(this, ClipSendActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
          .putExtra("silent", true),
      )
    } catch (e: Exception) {
      Log.w(TAG, "không mở được ClipSendActivity", e)
    }
  }

  override fun onInterrupt() {}

  override fun onUnbind(intent: Intent?): Boolean {
    main.removeCallbacks(launch)
    unregister(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    main.removeCallbacks(launch)
    unregister(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
    super.onDestroy()
  }

  private fun unregister(cm: ClipboardManager) {
    current?.let { cm.removePrimaryClipChangedListener(it) }
    current = null
  }

  /** "Copy" theo ngôn ngữ máy (chuỗi hệ thống) + vài cụm phổ biến. */
  private fun copyKeywords(): Set<String> {
    val sys = Resources.getSystem()
    val fromSystem = listOf(android.R.string.copy, android.R.string.copyUrl).mapNotNull {
      try { sys.getString(it).trim().lowercase() } catch (e: Exception) { null }
    }
    return (fromSystem + listOf("copy", "sao chép")).toSet()
  }

  companion object {
    private const val TAG = "ClipA11y"
    private val COPIED_RE = Regex("""\bcopied\b|đã sao chép|đã chép""")
    // menu giữ lâu / bottom sheet của Slack, Discord, Zalo, Chrome: "Copy text", "Copy image", "Sao chép văn bản"...
    private val COPY_ACTION_RE = Regex("""^(copy|sao chép)\s+(text|image|photo|link|link address|message|selection|văn bản|ảnh|hình ảnh|liên kết|đường dẫn|tin nhắn|nội dung)\b.{0,20}$""")

    @Volatile
    private var current: ClipboardManager.OnPrimaryClipChangedListener? = null
  }
}
