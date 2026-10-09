package com.kokoropie.notification

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Kiểm tra bản mới trên GitHub Releases, tải APK và mở trình cài đặt của hệ thống. */
object Updater {
  private const val REPO = "kokoropie/sync-notifications-android"

  class Release(val version: String, val notes: String, val apkUrl: String?, val size: Long)

  fun currentVersion(ctx: Context): String =
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()

  fun fetchLatest(): Release {
    val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
    try {
      conn.connectTimeout = 10_000
      conn.readTimeout = 15_000
      conn.setRequestProperty("Accept", "application/vnd.github+json")
      conn.setRequestProperty("User-Agent", "SyncNotification-Android")
      if (conn.responseCode != 200) throw java.io.IOException("Không lấy được thông tin bản phát hành (HTTP ${conn.responseCode})")
      val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
      val tag = json.getString("tag_name")
      val assets = json.optJSONArray("assets")
      var url: String? = null
      var size = 0L
      if (assets != null) for (i in 0 until assets.length()) {
        val a = assets.getJSONObject(i)
        if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); size = a.optLong("size"); break }
      }
      return Release(tag.removePrefix("v"), json.optString("body"), url, size)
    } finally {
      conn.disconnect()
    }
  }

  /** So sánh 1.2.3 với 1.10.0; bản có hậu tố (-beta, -dev) nhỏ hơn bản chính thức cùng số. */
  fun isNewer(a: String, b: String): Boolean {
    fun parse(v: String): Pair<List<Int>, Boolean> {
      val parts = v.split("-", limit = 2)
      return parts[0].split(".").map { it.toIntOrNull() ?: 0 } to (parts.size > 1)
    }
    val (x, xPre) = parse(a)
    val (y, yPre) = parse(b)
    for (i in 0 until maxOf(x.size, y.size)) {
      val p = x.getOrElse(i) { 0 }
      val q = y.getOrElse(i) { 0 }
      if (p != q) return p > q
    }
    return !xPre && yPre
  }

  /** Tải APK về cache của app. */
  fun download(ctx: Context, url: String): File {
    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val out = File(dir, "update.apk")
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
      conn.connectTimeout = 15_000
      conn.readTimeout = 30_000
      conn.setRequestProperty("User-Agent", "SyncNotification-Android")
      if (conn.responseCode != 200) throw java.io.IOException("Tải thất bại (HTTP ${conn.responseCode})")
      conn.inputStream.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
    } finally {
      conn.disconnect()
    }
    return out
  }

  /** APK mới phải cùng package và cùng chứng chỉ ký với bản đang cài, nếu không hệ thống sẽ từ chối cài đè. */
  @Suppress("DEPRECATION")
  fun verify(ctx: Context, apk: File) {
    val pm = ctx.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: throw IllegalStateException("File APK không hợp lệ.")
    if (archive.packageName != ctx.packageName) throw IllegalStateException("APK không phải của app này.")
    fun sigs(info: android.content.pm.PackageInfo): Set<String> =
      (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
        ?.map { it.toCharsString() }?.toSet().orEmpty()
    val current = pm.getPackageInfo(ctx.packageName, flags)
    if (sigs(archive) != sigs(current)) {
      throw IllegalStateException("APK mới được ký bằng khóa khác với bản đang cài nên không cài đè được. Hãy gỡ app và cài lại một lần.")
    }
  }

  fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

  /** Mở màn hình cấp quyền "cài app không rõ nguồn gốc" cho riêng app này. */
  fun openInstallPermission(ctx: Context) {
    ctx.startActivity(
      Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }

  fun launchInstaller(ctx: Context, apk: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.update", apk)
    ctx.startActivity(
      Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }
}
