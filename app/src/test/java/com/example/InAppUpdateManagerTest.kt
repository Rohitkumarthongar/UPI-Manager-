package com.example

import com.example.util.InAppUpdateManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InAppUpdateManagerTest {
  @Test fun validManifestIsAccepted() {
    val config = InAppUpdateManager.parseVersionJson("""{"latestVersionCode":2,"latestVersionName":"1.1","apkDownloadUrl":"https://example.com/app.apk"}""")
    assertEquals(2L, config?.latestVersionCode)
  }

  @Test fun incompleteOrUnsafeManifestsAreRejected() {
    assertNull(InAppUpdateManager.parseVersionJson("{}"))
    assertNull(InAppUpdateManager.parseVersionJson("""{"latestVersionCode":2,"latestVersionName":"1.1","apkDownloadUrl":"http://example.com/app.apk"}"""))
  }
}
