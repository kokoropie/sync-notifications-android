package com.kokoropie.notification

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Lắng nghe mọi thông báo trên Android và đẩy lên server. */
class NotificationService : NotificationListenerService() {
  // key thông báo -> chữ ký nội dung lần cuối, tránh gửi lại khi app chỉ cập nhật progress...
  private val seen = ConcurrentHashMap<String, String>()

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    if (!Prefs.get(this).isConfigured) return
    if (sbn.packageName == packageName) return
    Whitelist.refreshIfStale(this)
    if (!Prefs.get(this).allows(sbn.packageName)) return
    val n = sbn.notification
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
    if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 && n.category != Notification.CATEGORY_CALL) return
    if (n.category == Notification.CATEGORY_CALL) return // cuộc gọi do CallReceiver xử lý
    if (n.category == Notification.CATEGORY_PROGRESS) return

    val ex = n.extras
    val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
    val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
    if (title.isEmpty() && text.isEmpty()) return

    val sig = "$title|$text|$sub"
    if (seen.put(sbn.key, sig) == sig) return
    if (seen.size > 500) seen.clear()

    val payload = JSONObject()
      .put("type", "notification")
      .put("id", "${sbn.key}|${sbn.postTime}".take(64))
      .put("timestamp", sbn.postTime)
      .put("packageName", sbn.packageName)
      .put("appName", appLabel(sbn.packageName))
      .put("title", title)
      .put("text", text)
      .put("subText", sub)
    Api.sendEvent(this, payload, sbn.packageName)
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification) {
    seen.remove(sbn.key)
  }

  private fun appLabel(pkg: String): String = try {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
  } catch (e: PackageManager.NameNotFoundException) {
    pkg
  }
}
