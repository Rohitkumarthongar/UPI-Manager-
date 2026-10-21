package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared between UI catchup and WorkManager so simultaneous starts do not duplicate fetches. */
object UpdateChecks {
  private val lock = Mutex()

  fun installedVersion(context: Context): Long = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
      context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    else {
      @Suppress("DEPRECATION")
      context.packageManager.getPackageInfo(context.packageName, 0)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
      @Suppress("DEPRECATION")
      info.versionCode.toLong()
    }
  } catch (_: PackageManager.NameNotFoundException) { Long.MAX_VALUE }

  suspend fun check(context: Context, force: Boolean = false,
    url: String = InAppUpdateManager.DEFAULT_VERSION_URL): AppVersionConfig? = lock.withLock {
    val store = UpdateCheckStore(context)
    if (!force && !store.isDue()) return@withLock store.available(installedVersion(context))
    val config = InAppUpdateManager.fetchRemoteVersionConfig(url) ?: return@withLock null
    store.saveSuccess(config, installedVersion(context))
    config
  }
}
