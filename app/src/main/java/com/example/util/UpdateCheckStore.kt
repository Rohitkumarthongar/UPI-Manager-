package com.example.util

import android.content.Context
import java.util.concurrent.TimeUnit

/** Device-local update metadata. Never contains an APK or installation permission. */
class UpdateCheckStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("update_check", Context.MODE_PRIVATE)

  val lastSuccessfulCheck: Long get() = prefs.getLong("last_success", 0L)

  fun isDue(now: Long = System.currentTimeMillis()): Boolean =
    lastSuccessfulCheck <= 0L || now < lastSuccessfulCheck ||
      now - lastSuccessfulCheck >= CHECK_INTERVAL_MS

  fun available(installedVersion: Long): AppVersionConfig? {
    val code = prefs.getLong("code", 0L)
    if (code <= installedVersion) {
      if (code > 0) clearAvailable()
      return null
    }
    val config = InAppUpdateManager.parseVersionJson(prefs.getString("config", null) ?: return null)
    if (config?.latestVersionCode != code) {
      clearAvailable()
      return null
    }
    return config
  }

  fun visible(installedVersion: Long): AppVersionConfig? =
    available(installedVersion)?.takeUnless { prefs.getLong("dismissed_code", 0L) == it.latestVersionCode }

  fun saveSuccess(config: AppVersionConfig, installedVersion: Long, now: Long = System.currentTimeMillis()) {
    val editor = prefs.edit().putLong("last_success", now)
    if (config.latestVersionCode > installedVersion) {
      editor.putLong("code", config.latestVersionCode)
        .putString("config", org.json.JSONObject().apply {
          put("latestVersionCode", config.latestVersionCode)
          put("latestVersionName", config.latestVersionName)
          put("apkDownloadUrl", config.apkDownloadUrl)
          put("isMandatory", config.isMandatory)
          put("releaseNotes", config.releaseNotes)
        }.toString())
    } else {
      editor.remove("code").remove("config")
    }
    editor.commit()
  }

  fun dismiss(code: Long) { prefs.edit().putLong("dismissed_code", code).apply() }

  private fun clearAvailable() { prefs.edit().remove("code").remove("config").apply() }

  companion object { val CHECK_INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(7) }
}
