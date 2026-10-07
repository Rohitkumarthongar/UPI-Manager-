package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

data class AppVersionConfig(
  val latestVersionCode: Long,
  val latestVersionName: String,
  val apkDownloadUrl: String,
  val isMandatory: Boolean,
  val releaseNotes: String = "Bug fixes and performance enhancements."
)

object InAppUpdateManager {

  private const val TAG = "InAppUpdateManager"

  // Default version check URL hosted on Firebase Hosting
  const val DEFAULT_VERSION_URL = "https://upi-manager-b2087.web.app/version.json"

  /**
   * Fetches the remote version.json file asynchronously with redirect support.
   */
  suspend fun fetchRemoteVersionConfig(configUrl: String = DEFAULT_VERSION_URL): AppVersionConfig? {
    return withContext(Dispatchers.IO) {
      try {
        var currentUrl = configUrl
        var redirectCount = 0

        while (redirectCount < 5) {
          val url = URL(currentUrl)
          val connection = url.openConnection() as HttpURLConnection
          connection.instanceFollowRedirects = true
          connection.requestMethod = "GET"
          connection.connectTimeout = 8000
          connection.useCaches = false
          connection.setRequestProperty("Cache-Control", "no-cache")
          connection.readTimeout = 8000

          val status = connection.responseCode
          if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
            status == HttpURLConnection.HTTP_MOVED_PERM ||
            status == 307 || status == 308
          ) {
            val newUrl = connection.getHeaderField("Location")
            if (newUrl.isNullOrBlank()) break
            currentUrl = newUrl
            redirectCount++
            continue
          }

          if (status == HttpURLConnection.HTTP_OK) {
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            return@withContext parseVersionJson(responseText)
          } else {
            Log.e(TAG, "HTTP Error fetching version.json: $status")
            break
          }
        }
        null
      } catch (e: Exception) {
        Log.e(TAG, "Error checking for remote update", e)
        null
      }
    }
  }

  fun parseVersionJson(jsonString: String): AppVersionConfig? {
    return try {
      val json = JSONObject(jsonString)
      val code = json.optLong("latestVersionCode", 0L)
      val name = json.optString("latestVersionName", "")
      val apkUrl = json.optString("apkDownloadUrl", "")
      if (code <= 0 || name.isBlank() || Uri.parse(apkUrl).scheme != "https" || Uri.parse(apkUrl).host.isNullOrBlank()) return null
      AppVersionConfig(
        latestVersionCode = code,
        latestVersionName = name,
        apkDownloadUrl = apkUrl,
        isMandatory = json.optBoolean("isMandatory", false),
        releaseNotes = json.optString("releaseNotes", "New update available!")
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse version.json", e)
      null
    }
  }

  /**
   * Compares the current package versionCode with the remote latestVersionCode.
   */
  fun isUpdateAvailable(context: Context, config: AppVersionConfig): Boolean {
    val currentVersionCode = try {
      val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
      }
    } catch (e: Exception) {
      1L
    }

    return config.latestVersionCode > currentVersionCode
  }

  /**
   * Downloads the APK file into local cache directory and reports progress.
   */
  suspend fun downloadApk(
    context: Context,
    apkUrl: String,
    onProgress: (Int) -> Unit
  ): File? {
    return withContext(Dispatchers.IO) {
      val apkFile = File(context.cacheDir, "apks/update.apk")
      downloadApkToFile(apkUrl, apkFile, { url -> url.openConnection() as HttpURLConnection }) { progress ->
        withContext(Dispatchers.Main) { onProgress(progress) }
      }
    }
  }

  /** Connection factory keeps the network behavior testable without external release servers. */
  internal suspend fun downloadApkToFile(
    apkUrl: String,
    apkFile: File,
    openConnection: (URL) -> HttpURLConnection,
    onProgress: suspend (Int) -> Unit = {}
  ): File? {
    // A failed attempt must not leave a previously downloaded APK available for installation.
    if (apkFile.exists() && !apkFile.delete()) return null
    var completed = false
    try {
      var url = URL(apkUrl)
      var redirects = 0
      while (true) {
        if (url.protocol != "https" || url.host.isNullOrBlank()) return null
        val connection = openConnection(url)
        try {
          connection.instanceFollowRedirects = false
          connection.requestMethod = "GET"
          connection.connectTimeout = 10000
          connection.readTimeout = 20000
          val status = connection.responseCode
          if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            if (redirects >= 5) return null
            val location = connection.getHeaderField("Location")?.takeIf { it.isNotBlank() } ?: return null
            url = URL(url, location)
            redirects++
            continue
          }
          if (status != HttpURLConnection.HTTP_OK) {
            Log.e(TAG, "Failed to connect for download: $status")
            return null
          }

          val expectedLength = connection.contentLengthLong
          val parent = apkFile.absoluteFile.parentFile ?: return null
          if (!parent.exists() && !parent.mkdirs()) return null
          connection.inputStream.use { input ->
            FileOutputStream(apkFile).use { output ->
              val data = ByteArray(4096)
              var total = 0L
              while (true) {
                val count = input.read(data)
                if (count == -1) break
                output.write(data, 0, count)
                total += count
                if (expectedLength > 0) onProgress(((total * 100) / expectedLength).coerceAtMost(100).toInt())
              }
            }
          }
          if (apkFile.length() == 0L || (expectedLength >= 0 && apkFile.length() != expectedLength) ||
            !isApkArchive(apkFile)
          ) return null
          onProgress(100)
          completed = true
          return apkFile
        } finally {
          connection.disconnect()
        }
      }
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error downloading APK", e)
      return null
    } finally {
      // Only leave a completed, validated archive behind.
      if (!completed) apkFile.delete()
    }
  }

  private fun isApkArchive(file: File): Boolean = try {
    ZipFile(file).use { archive -> archive.getEntry("AndroidManifest.xml") != null }
  } catch (_: Exception) {
    false
  }

  /**
   * Triggers the Android Package Installer intent to install the downloaded APK.
   */
  fun promptInstallApk(context: Context, apkFile: File) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
        context.getSharedPreferences("app_update", Context.MODE_PRIVATE).edit().putBoolean("pending_install", true).apply()
        val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
          data = Uri.parse("package:${context.packageName}")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(manageIntent)
        Toast.makeText(context, "Please allow 'Install Unknown Apps' permission to install update", Toast.LENGTH_LONG).show()
        return
      }

      val apkUri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
      )

      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
      }
      context.startActivity(intent)
      context.getSharedPreferences("app_update", Context.MODE_PRIVATE).edit().remove("pending_install").apply()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to launch package installer", e)
      Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  /** Only resume a download the user already requested, after Android grants install access. */
  fun resumePendingInstall(context: Context) {
    val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("pending_install", false)) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) return
    val apk = File(context.cacheDir, "apks/update.apk")
    if (apk.exists() && apk.length() > 0) promptInstallApk(context, apk)
    else prefs.edit().remove("pending_install").apply()
  }
}
