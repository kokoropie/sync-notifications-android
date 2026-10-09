package com.kokoropie.notification

import android.content.Context
import java.util.UUID

/** Cấu hình dùng chung giữa UI (React Native) và các service chạy nền (Kotlin thuần). */
class Prefs private constructor(ctx: Context) {
  private val sp = ctx.applicationContext.getSharedPreferences("notify", Context.MODE_PRIVATE)

  var serverUrl: String
    get() = sp.getString("serverUrl", "") ?: ""
    set(v) = sp.edit().putString("serverUrl", v.trim().trimEnd('/')).apply()

  var accountKey: String
    get() = sp.getString("accountKey", "") ?: ""
    set(v) = sp.edit().putString("accountKey", v.trim()).apply()

  var deviceName: String
    get() = sp.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
    set(v) = sp.edit().putString("deviceName", v).apply()

  var clipboardSync: Boolean
    get() = sp.getBoolean("clipboardSync", true)
    set(v) = sp.edit().putBoolean("clipboardSync", v).apply()

  /** Bật = chỉ gửi thông báo của các app trong whitelist. Tắt = gửi tất cả. */
  var whitelistEnabled: Boolean
    get() = sp.getBoolean("whitelistEnabled", false)
    set(v) = sp.edit().putBoolean("whitelistEnabled", v).apply()

  var whitelist: Set<String>
    get() = sp.getStringSet("whitelist", emptySet()) ?: emptySet()
    set(v) = sp.edit().putStringSet("whitelist", v.toSet()).apply()

  /** true = đã sửa whitelist ở máy nhưng chưa đẩy lên server được. */
  var whitelistDirty: Boolean
    get() = sp.getBoolean("whitelistDirty", false)
    set(v) = sp.edit().putBoolean("whitelistDirty", v).apply()

  fun allows(pkg: String) = !whitelistEnabled || pkg in whitelist

  /** Các package đã upload icon lên server. */
  fun iconSent(pkg: String) = pkg in (sp.getStringSet("iconsSent", emptySet()) ?: emptySet())
  fun markIconSent(pkg: String) {
    val cur = HashSet(sp.getStringSet("iconsSent", emptySet()) ?: emptySet())
    cur.add(pkg)
    sp.edit().putStringSet("iconsSent", cur).apply()
  }

  val deviceId: String
    get() = sp.getString("deviceId", null) ?: ("android-" + UUID.randomUUID().toString().lowercase()).also {
      sp.edit().putString("deviceId", it).apply()
    }

  val isConfigured: Boolean get() = serverUrl.isNotEmpty() && accountKey.isNotEmpty()

  companion object {
    @Volatile private var inst: Prefs? = null
    fun get(ctx: Context): Prefs = inst ?: synchronized(this) { inst ?: Prefs(ctx).also { inst = it } }
  }
}
