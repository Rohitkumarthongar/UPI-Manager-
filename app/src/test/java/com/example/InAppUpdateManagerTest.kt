package com.example

import com.example.util.InAppUpdateManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InAppUpdateManagerTest {
  @get:Rule val files = TemporaryFolder()

  @Test fun validManifestIsAccepted() {
    val config = InAppUpdateManager.parseVersionJson("""{"latestVersionCode":2,"latestVersionName":"1.1","apkDownloadUrl":"https://example.com/app.apk"}""")
    assertEquals(2L, config?.latestVersionCode)
  }

  @Test fun incompleteOrUnsafeManifestsAreRejected() {
    assertNull(InAppUpdateManager.parseVersionJson("{}"))
    assertNull(InAppUpdateManager.parseVersionJson("""{"latestVersionCode":2,"latestVersionName":"1.1","apkDownloadUrl":"http://example.com/app.apk"}"""))
  }

  private fun apkBytes(): ByteArray = ByteArrayOutputStream().also { bytes ->
    ZipOutputStream(bytes).use { zip ->
      zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
      zip.write("manifest".toByteArray())
      zip.closeEntry()
    }
  }.toByteArray()

  private class Reply(url: URL, private val status: Int, private val location: String? = null,
                      private val body: ByteArray = byteArrayOf(), private val length: Long = body.size.toLong()) : HttpURLConnection(url) {
    var disconnected = false
    override fun getResponseCode() = status
    override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
    override fun getContentLengthLong() = length
    override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    override fun disconnect() { disconnected = true }
    override fun usingProxy() = false
    override fun connect() {}
  }

  @Test fun crossHostAndRelativeRedirectsDownloadAndDisconnectEveryHop() = runBlocking {
    val payload = apkBytes()
    val seen = mutableListOf<String>()
    val replies = mutableListOf<Reply>()
    val routes = mapOf(
      "https://github.com/org/repo/releases/download/v1/app.apk" to Pair(302, "https://objects.githubusercontent.com/assets/app.apk"),
      "https://objects.githubusercontent.com/assets/app.apk" to Pair(307, "../final/app.apk")
    )
    val progress = mutableListOf<Int>()
    val output = files.newFolder("apks").resolve("update.apk")
    val result = InAppUpdateManager.downloadApkToFile("https://github.com/org/repo/releases/download/v1/app.apk", output, { url ->
      seen.add(url.toString())
      val redirect = routes[url.toString()]
      Reply(url, redirect?.first ?: 200, redirect?.second, if (redirect == null) payload else byteArrayOf()).also(replies::add)
    }, progress::add)
    assertEquals(output, result)
    assertArrayEquals(payload, output.readBytes())
    assertEquals("https://objects.githubusercontent.com/final/app.apk", seen.last())
    assertEquals(3, seen.size)
    assertTrue(replies.all { it.disconnected })
    assertEquals(100, progress.last())
  }

  @Test fun redirectLimitAndDowngradeNeverOpenUnsafeTarget() = runBlocking {
    for (downgrade in listOf(true, false)) {
      val replies = mutableListOf<Reply>()
      val seen = mutableListOf<String>()
      val output = files.root.resolve("redirect-$downgrade.apk")
      output.writeBytes(apkBytes()) // stale file must not survive failed download
      val result = InAppUpdateManager.downloadApkToFile("https://github.com/0", output, { url ->
        seen.add(url.toString())
        Reply(url, 302, if (downgrade) "http://elsewhere.test/app.apk" else "/${seen.size}").also(replies::add)
      })
      assertNull(result)
      assertFalse(output.exists())
      assertEquals(if (downgrade) 1 else 6, seen.size)
      assertTrue(seen.all { it.startsWith("https://") })
      assertTrue(replies.all { it.disconnected })
    }
  }

  @Test fun errorsMissingLocationEmptyTruncatedAndHtmlAreNotInstallable() = runBlocking {
    val apk = apkBytes()
    val scenarios = listOf(
      Reply(URL("https://example.com/app.apk"), 404, body = "<html>error</html>".toByteArray()),
      Reply(URL("https://example.com/app.apk"), 302),
      Reply(URL("https://example.com/app.apk"), 200, body = byteArrayOf()),
      Reply(URL("https://example.com/app.apk"), 200, body = apk, length = apk.size.toLong() + 1),
      Reply(URL("https://example.com/app.apk"), 200, body = "<html>error</html>".toByteArray()),
      Reply(URL("https://example.com/app.apk"), 200, body = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
          zip.putNextEntry(ZipEntry("error.html"))
          zip.write("error".toByteArray())
          zip.closeEntry()
        }
      }.toByteArray())
    )
    scenarios.forEachIndexed { index, reply ->
      val output = files.root.resolve("invalid-$index.apk")
      assertNull(InAppUpdateManager.downloadApkToFile("https://example.com/app.apk", output, { reply }))
      assertFalse(output.exists())
      assertTrue(reply.disconnected)
    }
  }

  @Test fun completeApkWithoutContentLengthIsAccepted() = runBlocking {
    val payload = apkBytes()
    val output = files.root.resolve("unknown-length.apk")
    val reply = Reply(URL("https://example.com/app.apk"), 200, body = payload, length = -1)
    assertEquals(output, InAppUpdateManager.downloadApkToFile("https://example.com/app.apk", output, { reply }))
    assertArrayEquals(payload, output.readBytes())
    assertTrue(reply.disconnected)
  }
}
