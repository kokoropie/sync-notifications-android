package com.kokoropie.notification

import android.util.Base64
import org.json.JSONArray
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Port của Kaga.php — phải giữ giống hệt bản Node (kaga.js) và Swift (Kaga.swift).
 * Định dạng: <b64(header [a,b,c,d])>.<rev(b64(flip(b64(mảng số)) + salt))>.<hmac-sha256 hex>
 */
object Kaga {
  private const val A = 3
  private const val B = 5
  private const val C = 7
  private const val D = 3
  private const val FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

  private fun mul(i: Int, b: Int) = ((i + 1) % b).let { if (it == 0) 1 else it }
  private fun b64e(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), FLAGS)
  private fun b64d(s: String): ByteArray = Base64.decode(s, FLAGS)
  private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

  private fun hmac(data: String, salt: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return hex(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
  }

  private fun flip(s: String, c: Int): String {
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
      val end = minOf(i + c, s.length)
      sb.append(StringBuilder(s.substring(i, end)).reverse())
      i = end
    }
    return sb.toString()
  }

  /** Salt đã xử lý từ key: base64url(8 ký tự cuối của md5 hex). */
  fun saltOf(key: String): String {
    val md5 = hex(MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8)))
    return b64e(md5.takeLast(8))
  }

  /** [salt] là kết quả của [saltOf]. */
  fun encode(input: String, salt: String): String {
    val s = b64e(salt + input)
    val sb = StringBuilder("[")
    s.forEachIndexed { i, ch ->
      var v = ch.code
      if (D >= 1) v = v shl (i % A)
      if (D >= 2) v *= mul(i, B)
      if (D >= 3) v = v xor i
      if (i > 0) sb.append(',')
      sb.append(v)
    }
    sb.append(']')
    val mid = StringBuilder(b64e(flip(b64e(sb.toString()), C) + salt)).reverse()
    val head = "${b64e("[$A,$B,$C,$D]")}.$mid"
    return "$head.${hmac(head, salt)}"
  }

  /** null nếu sai salt / bị sửa / hỏng. */
  fun decode(input: String, salt: String): String? {
    try {
      val parts = input.split('.')
      if (parts.size != 3) return null
      val (head, enc, hash) = parts
      if (!MessageDigest.isEqual(hash.toByteArray(), hmac("$head.$enc", salt).toByteArray())) return null

      val p = JSONArray(String(b64d(head), Charsets.UTF_8))
      if (p.length() != 4) return null
      val a = p.getInt(0); val b = p.getInt(1); val c = p.getInt(2); val d = p.getInt(3)
      if (a !in 1..8 || b !in 1..8 || c !in 1..8 || d !in 0..3) return null

      val encoded = String(b64d(StringBuilder(enc).reverse().toString()), Charsets.ISO_8859_1)
      if (!encoded.endsWith(salt)) return null
      val arr = JSONArray(String(b64d(flip(encoded.dropLast(salt.length), c)), Charsets.UTF_8))
      val bytes = ByteArray(arr.length())
      for (i in 0 until arr.length()) {
        var v = arr.getInt(i)
        if (d >= 3) v = v xor i
        if (d >= 2) v /= mul(i, b)
        if (d >= 1) v = v shr (i % a)
        if (v !in 0..255) return null
        bytes[i] = v.toByte()
      }
      val out = String(b64d(String(bytes, Charsets.ISO_8859_1)), Charsets.UTF_8)
      return if (out.startsWith(salt)) out.substring(salt.length) else null
    } catch (e: Exception) {
      return null
    }
  }

  /** JSON -> payload binary đã mã hoá. */
  fun toFrame(text: String, salt: String): ByteArray = encode(text, salt).toByteArray(Charsets.ISO_8859_1)

  /** null nếu frame sai/hỏng. */
  fun fromFrame(frame: ByteArray, salt: String): String? = decode(String(frame, Charsets.ISO_8859_1), salt)
}
