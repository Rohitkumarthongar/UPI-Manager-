package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.util.AppVersionConfig
import com.example.util.UpdateCheckScheduler
import com.example.util.UpdateCheckStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateCheckSchedulingTest {
  @Test fun sevenDayConnectedWorkHasStableUniqueName() {
    val request = UpdateCheckScheduler.request()
    assertEquals("weekly_version_check", UpdateCheckScheduler.WORK_NAME)
    assertEquals(ExistingPeriodicWorkPolicy.KEEP, UpdateCheckScheduler.EXISTING_POLICY)
    assertEquals(TimeUnit.DAYS.toMillis(7), request.workSpec.intervalDuration)
    assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    assertEquals(0L, request.workSpec.initialDelay)
  }

  @Test fun successPersistsAndDismissalSurvivesRecreationUntilNewerVersion() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("update_check", Context.MODE_PRIVATE).edit().clear().commit()
    val store = UpdateCheckStore(context)
    val version = AppVersionConfig(12, "1.2", "https://example.org/app.apk", false, "Fixes")
    assertTrue(store.isDue(1_000_000L))
    store.saveSuccess(version, 10, 1_000_000L)
    assertFalse(UpdateCheckStore(context).isDue(1_000_001L))
    assertTrue(store.isDue(1_000_000L + TimeUnit.DAYS.toMillis(7)))
    assertEquals(version, UpdateCheckStore(context).visible(10))
    store.dismiss(12)
    assertNull(UpdateCheckStore(context).visible(10))
    assertEquals(version, store.available(10)) // Notification tap / manual check can still open it.
    store.saveSuccess(version.copy(latestVersionCode = 13), 10, 2_000_000L)
    assertEquals(13L, UpdateCheckStore(context).visible(10)?.latestVersionCode)
    assertNull(store.available(13))
  }

  @Test fun successfulCheckWithoutUpdateKeepsTimestampButClearsOldConfig() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("update_check", Context.MODE_PRIVATE).edit().clear().commit()
    val store = UpdateCheckStore(context)
    val config = AppVersionConfig(3, "3", "https://example.org/app.apk", false)
    store.saveSuccess(config, 2, 5_000L)
    store.saveSuccess(config, 3, 6_000L)
    assertEquals(6_000L, store.lastSuccessfulCheck)
    assertNull(store.available(2))
  }
}
