package com.kokoropie.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import java.io.ByteArrayOutputStream

object AppIcons {
  /** Icon app dạng PNG vuông `size` px, base64 (không xuống dòng). Null nếu không có app. */
  fun base64(ctx: Context, pkg: String, size: Int): String? = try {
    val d = ctx.packageManager.getApplicationIcon(pkg)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, size, size)
    d.draw(Canvas(bmp))
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    bmp.recycle()
    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
  } catch (e: Exception) {
    null
  }
}
