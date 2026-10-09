package com.kokoropie.notification

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.NativeModule
import com.facebook.react.uimanager.ViewManager
import org.json.JSONObject

/** Cầu nối React Native <-> phần native chạy nền. */
class NotifyModule(private val rc: ReactApplicationContext) : ReactContextBaseJavaModule(rc) {
  override fun getName() = "NotifyModule"

  @ReactMethod
  fun getAppInfo(promise: Promise) {
    val v = rc.packageManager.getPackageInfo(rc.packageName, 0).versionName ?: ""
    promise.resolve(Arguments.createMap().apply { putString("version", v) })
  }

  @ReactMethod
  fun getConfig(promise: Promise) {
    val p = Prefs.get(rc)
    promise.resolve(Arguments.createMap().apply {
      putString("serverUrl", p.serverUrl)
      putString("accountKey", p.accountKey)
      putString("deviceName", p.deviceName)
      putBoolean("clipboardSync", p.clipboardSync)
    })
  }

  @ReactMethod
  fun saveConfig(cfg: ReadableMap, promise: Promise) {
    val p = Prefs.get(rc)
    p.serverUrl = cfg.getString("serverUrl").orEmpty()
    p.accountKey = cfg.getString("accountKey").orEmpty()
    p.deviceName = cfg.getString("deviceName").orEmpty().ifEmpty { android.os.Build.MODEL }
    p.clipboardSync = cfg.getBoolean("clipboardSync")
    SyncService.stop(rc)
    SyncService.start(rc)
    promise.resolve(null)
  }

  /** Đăng ký thiết bị + kiểm tra key/URL. */
  @ReactMethod
  fun testConnection(promise: Promise) {
    Api.io {
      try {
        val body = JSONObject().put("name", Prefs.get(rc).deviceName).put("platform", "android")
        Api.request(rc, "POST", "/api/devices", body)
        promise.resolve(true)
      } catch (e: Exception) {
        promise.reject("E_CONNECT", e.message, e)
      }
    }
  }

  /** Gửi một thông báo thử sang Mac để kiểm tra toàn bộ luồng Android → server → Mac. */
  @ReactMethod
  fun sendTestNotification(promise: Promise) {
    Api.io {
      try {
        val now = System.currentTimeMillis()
        val body = JSONObject()
          .put("type", "notification")
          .put("id", "test-$now")
          .put("timestamp", now)
          .put("packageName", rc.packageName)
          .put("appName", "Sync Notification")
          .put("title", "Thông báo thử")
          .put("text", "Nếu bạn thấy thông báo này trên Mac, đồng bộ đang hoạt động.")
        Api.request(rc, "POST", "/api/events", body)
        promise.resolve(true)
      } catch (e: Exception) {
        promise.reject("E_TEST", e.message, e)
      }
    }
  }

  @ReactMethod
  fun getStatus(promise: Promise) {
    val listeners = Settings.Secure.getString(rc.contentResolver, "enabled_notification_listeners").orEmpty()
    fun granted(perm: String) = rc.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    val pm = rc.getSystemService(Context.POWER_SERVICE) as PowerManager
    promise.resolve(Arguments.createMap().apply {
      putBoolean("notificationAccess", listeners.contains(rc.packageName))
      putBoolean("phoneState", granted(Manifest.permission.READ_PHONE_STATE))
      putBoolean("callLog", granted(Manifest.permission.READ_CALL_LOG))
      putBoolean("contacts", granted(Manifest.permission.READ_CONTACTS))
      val a11y = Settings.Secure.getString(rc.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
      putBoolean("clipboardAccessibility", a11y.contains("${rc.packageName}/${ClipboardAccessibilityService::class.java.name}"))
      putBoolean("batteryUnrestricted", pm.isIgnoringBatteryOptimizations(rc.packageName))
    })
  }

  @ReactMethod
  fun openNotificationAccessSettings() {
    rc.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  /** Danh sách app có icon launcher, kèm icon nhỏ (base64) để hiển thị trong UI chọn whitelist. */
  @ReactMethod
  fun getInstalledApps(promise: Promise) {
    Api.io {
      try {
        val pm = rc.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
          .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
          .distinctBy { it.first }
          .filter { it.first != rc.packageName }
          .sortedBy { it.second.lowercase() }
        val arr = Arguments.createArray()
        for ((pkg, label) in apps) {
          arr.pushMap(Arguments.createMap().apply {
            putString("packageName", pkg)
            putString("label", label)
            putString("icon", AppIcons.base64(rc, pkg, 48))
          })
        }
        promise.resolve(arr)
      } catch (e: Exception) {
        promise.reject("E_APPS", e.message, e)
      }
    }
  }

  /** Server là nguồn chính (dùng chung với Mac); máy có thay đổi chưa đồng bộ thì đẩy lên thay vì kéo về. */
  @ReactMethod
  fun getWhitelist(promise: Promise) {
    Api.io {
      Whitelist.sync(rc)
      val p = Prefs.get(rc)
      promise.resolve(Arguments.createMap().apply {
        putBoolean("enabled", p.whitelistEnabled)
        putArray("packages", Arguments.fromList(p.whitelist.toList()))
      })
    }
  }

  @ReactMethod
  fun setWhitelist(enabled: Boolean, packages: com.facebook.react.bridge.ReadableArray, promise: Promise) {
    val p = Prefs.get(rc)
    p.whitelistEnabled = enabled
    p.whitelist = (0 until packages.size()).mapNotNull { packages.getString(it) }.toSet()
    p.whitelistDirty = true
    promise.resolve(null)
    Api.io { if (p.isConfigured) Whitelist.push(rc) }
  }

  @ReactMethod
  fun openAccessibilitySettings() {
    rc.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  @ReactMethod
  fun requestIgnoreBatteryOptimizations() {
    rc.startActivity(
      Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${rc.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }

  /** Gửi clipboard hiện tại (app đang foreground nên đọc được). */
  @ReactMethod
  fun sendClipboard(promise: Promise) {
    val cm = rc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(rc)?.toString()
    if (text.isNullOrEmpty()) return promise.reject("E_EMPTY", "Clipboard trống")
    Api.io {
      try {
        Api.request(rc, "POST", "/api/clipboard", JSONObject().put("text", text))
        promise.resolve(true)
      } catch (e: Exception) {
        promise.reject("E_SEND", e.message, e)
      }
    }
  }
}

class NotifyPackage : ReactPackage {
  override fun createNativeModules(c: ReactApplicationContext): List<NativeModule> = listOf(NotifyModule(c))
  override fun createViewManagers(c: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}
