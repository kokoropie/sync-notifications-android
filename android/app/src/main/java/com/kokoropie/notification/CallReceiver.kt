package com.kokoropie.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.util.UUID

/** Phát hiện cuộc gọi đến / đã nghe / nhỡ qua PHONE_STATE. */
class CallReceiver : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
    if (!Prefs.get(ctx).isConfigured) return
    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
    @Suppress("DEPRECATION")
    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

    synchronized(Companion) {
      when (state) {
        TelephonyManager.EXTRA_STATE_RINGING -> {
          // Android gửi RINGING 2 lần (có/không số); chỉ gửi khi có thêm thông tin mới
          if (lastState == state && (number.isNullOrEmpty() || number == lastNumber)) return
          lastState = state
          if (!number.isNullOrEmpty()) lastNumber = number
          wasAnswered = false
          emit(ctx, "ringing", lastNumber)
        }
        TelephonyManager.EXTRA_STATE_OFFHOOK -> {
          if (lastState == TelephonyManager.EXTRA_STATE_RINGING) {
            wasAnswered = true
            emit(ctx, "answered", lastNumber)
          }
          lastState = state
        }
        TelephonyManager.EXTRA_STATE_IDLE -> {
          if (lastState == TelephonyManager.EXTRA_STATE_RINGING && !wasAnswered) emit(ctx, "missed", lastNumber)
          lastState = state
          lastNumber = null
          wasAnswered = false
        }
      }
    }
  }

  private fun emit(ctx: Context, callState: String, number: String?) {
    val payload = JSONObject()
      .put("type", "call")
      .put("id", UUID.randomUUID().toString())
      .put("timestamp", System.currentTimeMillis())
      .put("state", callState)
      .put("number", number ?: "")
      .put("name", contactName(ctx, number).orEmpty())
    Api.sendEvent(ctx, payload)
  }

  private fun contactName(ctx: Context, number: String?): String? {
    if (number.isNullOrEmpty()) return null
    if (ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
    return try {
      val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
      ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
      }
    } catch (e: Exception) {
      null
    }
  }

  companion object {
    private var lastState: String = TelephonyManager.EXTRA_STATE_IDLE
    private var lastNumber: String? = null
    private var wasAnswered = false
  }
}
