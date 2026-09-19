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
      AppVersionConfig(
        latestVersionCode = json.optLong("latestVersionCode", 1L),
        latestVersionName = json.optString("latestVersionName", "1.0"),
        apkDownloadUrl = json.optString("apkDownloadUrl", ""),
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
      try {
        var currentUrl = apkUrl
        var redirectCount = 0
        var connection: HttpURLConnection? = null

        while (redirectCount < 5) {
          val url = URL(currentUrl)
          connection = url.openConnection() as HttpURLConnection
          connection.instanceFollowRedirects = true
          connection.requestMethod = "GET"
          connection.connectTimeout = 10000
          connection.readTimeout = 20000

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
          break
        }

        if (connection == null || connection.responseCode != HttpURLConnection.HTTP_OK) {
          Log.e(TAG, "Failed to connect for download: ${connection?.responseCode}")
          return@withContext null
        }

        val fileLength = connection.contentLength
        val apkDir = File(context.cacheDir, "apks")
        if (!apkDir.exists()) apkDir.mkdirs()

        val apkFile = File(apkDir, "update.apk")
        if (apkFile.exists()) apkFile.delete()

        connection.inputStream.use { input ->
          FileOutputStream(apkFile).use { output ->
            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
              total += count
              if (fileLength > 0) {
                val progress = ((total * 100) / fileLength).toInt()
                withContext(Dispatchers.Main) {
                  onProgress(progress)
                }
              }
              output.write(data, 0, count)
            }
            output.flush()
          }
        }
        apkFile
      } catch (e: Exception) {
        Log.e(TAG, "Error downloading APK", e)
        null
      }
    }
  }

  /**
   * Triggers the Android Package Installer intent to install the downloaded APK.
   */
  fun promptInstallApk(context: Context, apkFile: File) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
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
    } catch (e: Exception) {
      Log.e(TAG, "Failed to launch package installer", e)
      Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }
}
