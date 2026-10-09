package com.kokoropie.notification

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService

/** Ô Quick Settings: bấm để gửi clipboard hiện tại sang Mac. */
class ClipTileService : TileService() {
  override fun onClick() {
    val i = Intent(this, ClipSendActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (android.os.Build.VERSION.SDK_INT >= 34) {
      startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
    } else {
      @Suppress("DEPRECATION")
      startActivityAndCollapse(i)
    }
  }
}
