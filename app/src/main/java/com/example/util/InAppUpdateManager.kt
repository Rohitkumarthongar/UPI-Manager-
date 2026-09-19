package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
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
   * Fetches the remote version.json file asynchronously.
   */
  suspend fun fetchRemoteVersionConfig(configUrl: String = DEFAULT_VERSION_URL): AppVersionConfig? {
    return withContext(Dispatchers.IO) {
      try {
        val url = URL(configUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
          val responseText = connection.inputStream.bufferedReader().use { it.readText() }
          parseVersionJson(responseText)
        } else {
          Log.e(TAG, "HTTP Error fetching version.json: ${connection.responseCode}")
          null
        }
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
        val url = URL(apkUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        connection.connect()

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
    }
  }
}
