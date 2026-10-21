package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val store = UpdateCheckStore(applicationContext)
    // Periodic work must fetch on each weekly occurrence. A recent manual check
    // must not skip this occurrence and stretch the background gap to 14 days.
    val config = UpdateChecks.check(applicationContext, force = true) ?: return Result.retry()
    val installed = UpdateChecks.installedVersion(applicationContext)
    if (store.visible(installed) != null) notifyAvailable(config)
    return Result.success()
  }

  private fun notifyAvailable(config: AppVersionConfig) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
    }
    val intent = Intent(applicationContext, MainActivity::class.java).apply {
      putExtra(EXTRA_OPEN_UPDATE, true)
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val pending = PendingIntent.getActivity(applicationContext, 0, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(applicationContext, CHANNEL)
      .setSmallIcon(applicationContext.applicationInfo.icon)
      .setContentTitle("UPIManager update available")
      .setContentText("Version ${config.latestVersionName} is ready to review")
      .setContentIntent(pending)
      .setAutoCancel(true)
      .build())
  }

  companion object {
    const val EXTRA_OPEN_UPDATE = "com.example.OPEN_CACHED_UPDATE"
    private const val CHANNEL = "app_updates"
    private const val NOTIFICATION_ID = 1307
  }
}
