package com.example.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UpdateCheckScheduler {
  const val WORK_NAME = "weekly_version_check"
  val EXISTING_POLICY = ExistingPeriodicWorkPolicy.KEEP

  fun request(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(7, TimeUnit.DAYS)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .build()

  fun schedule(context: Context) {
    WorkManager.getInstance(context.applicationContext)
      .enqueueUniquePeriodicWork(WORK_NAME, EXISTING_POLICY, request())
  }
}
