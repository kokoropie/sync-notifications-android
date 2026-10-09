package com.kokoropie.notification

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Đồng bộ whitelist với server (dùng chung với Mac theo account key). Gọi trên thread nền. */
object Whitelist {
  @Volatile private var lastPull = 0L

  /** true = thành công. */
  fun push(ctx: Context): Boolean = try {
    val p = Prefs.get(ctx)
    val body = JSONObject().put("enabled", p.whitelistEnabled).put("packages", JSONArray(p.whitelist.toList()))
    Api.request(ctx, "PUT", "/api/whitelist", body)
    p.whitelistDirty = false
    true
  } catch (e: Exception) { false }

  /** Server là nguồn chính; nếu máy có thay đổi chưa đẩy được thì đẩy lên thay vì kéo về. */
  fun sync(ctx: Context) {
    val p = Prefs.get(ctx)
    if (!p.isConfigured) return
    lastPull = System.currentTimeMillis()
    if (p.whitelistDirty) { push(ctx); return }
    try {
      val o = JSONObject(Api.request(ctx, "GET", "/api/whitelist"))
      val arr = o.optJSONArray("packages")
      p.whitelist = (0 until (arr?.length() ?: 0)).map { arr!!.getString(it) }.toSet()
      p.whitelistEnabled = o.optBoolean("enabled")
    } catch (e: Exception) { /* offline: dùng bản local */ }
  }

  /** Gọi mỗi khi có thông báo: kéo lại whitelist nếu đã quá 60s (để nhận thay đổi từ Mac). */
  fun refreshIfStale(ctx: Context) {
    if (System.currentTimeMillis() - lastPull < 60_000) return
    lastPull = System.currentTimeMillis()
    Api.io { sync(ctx) }
  }
}
