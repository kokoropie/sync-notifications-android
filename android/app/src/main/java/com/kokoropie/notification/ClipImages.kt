package com.kokoropie.notification

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Clipboard dạng ảnh: upload raw qua HTTP (POST /api/clipboard/image), server chỉ báo hash qua WebSocket,
 * máy nhận tải về rồi đặt lên clipboard bằng content URI của FileProvider.
 */
object ClipImages {
  private const val TAG = "ClipImages"
  const val MAX_BYTES = 10 * 1024 * 1024
  private const val MAX_DIM = 4096
  private val SUPPORTED = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

  /** Hash ảnh vừa gửi / vừa nhận (dùng chung SyncService + ClipSendActivity) để chống vòng lặp. */
  @Volatile var lastHash: String? = null

  /** Clip có phải ảnh không (không đọc nội dung, gọi được trên main thread). */
  fun isImage(clip: ClipData?): Boolean =
    clip != null && clip.itemCount > 0 && clip.getItemAt(0).uri != null && clip.description.hasMimeType("image/*")

  /** Đọc ảnh đầu tiên của clip, chuẩn hoá về định dạng server nhận. Chạy ở thread nền. */
  fun read(ctx: Context, clip: ClipData?): Pair<ByteArray, String>? {
    if (!isImage(clip)) return null
    val uri = clip!!.getItemAt(0).uri
    // ảnh do chính app đặt lên (nhận từ Mac) thì không gửi ngược lại
    if (uri.authority == authority(ctx)) return null
    return readUri(ctx, uri)
  }

  fun readUri(ctx: Context, uri: Uri): Pair<ByteArray, String>? = try {
    val mime = ctx.contentResolver.getType(uri)?.lowercase()
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    when {
      bytes == null || mime == null || !mime.startsWith("image/") -> null
      mime in SUPPORTED && bytes.size <= MAX_BYTES -> bytes to mime
      else -> toJpeg(bytes) // HEIC... hoặc quá lớn
    }
  } catch (e: Exception) {
    Log.w(TAG, "không đọc được ảnh: ${e.message}")
    null
  }

  private fun toJpeg(bytes: ByteArray): Pair<ByteArray, String>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIM) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    try {
      for (q in intArrayOf(90, 70)) {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
        if (out.size() <= MAX_BYTES) return out.toByteArray() to "image/jpeg"
      }
      return null
    } finally {
      bmp.recycle()
    }
  }

  fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

  /** Gửi ảnh lên server nếu khác ảnh vừa gửi/nhận. Chạy ở thread nền. */
  fun send(ctx: Context, bytes: ByteArray, mime: String) {
    val hash = sha256(bytes)
    if (hash == lastHash) return
    lastHash = hash
    Api.upload(ctx, "/api/clipboard/image", bytes, mime)
  }

  /** Tải ảnh theo hash, lưu vào cache và trả về ClipData (content URI). Chạy ở thread nền. */
  fun download(ctx: Context, hash: String): ClipData? {
    if (!hash.matches(Regex("[0-9a-f]{64}"))) return null
    val (bytes, mime) = Api.download(ctx, "/api/clipboard/image/$hash")
    val ext = when (mime) { "image/jpeg" -> "jpg"; "image/gif" -> "gif"; "image/webp" -> "webp"; else -> "png" }
    val dir = File(ctx.cacheDir, "clipboard").apply { mkdirs() }
    // tên theo hash để app dán không dùng nhầm bản cache cũ; chỉ giữ ảnh mới nhất
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "$hash.$ext").apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(ctx, authority(ctx), file)
    return ClipData.newUri(ctx.contentResolver, "mac", uri)
  }

  private fun authority(ctx: Context) = "${ctx.packageName}.update"
}
