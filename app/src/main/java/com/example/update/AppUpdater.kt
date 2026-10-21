package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A newer app version published on GitHub Releases by .github/workflows/release.yml. */
data class AppUpdate(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val sha256: String?)

class UpdateException(message: String) : Exception(message)

/**
 * Checks for, downloads and installs app updates. Releases are public GitHub releases of [BuildConfig.UPDATE_REPO];
 * each carries a hidden line with its versionCode and the APK's SHA-256, which is verified before installing.
 * Android itself refuses an APK not signed with the same key as the installed app.
 */
class AppUpdater(private val context: Context) {
  private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
  private val dir get() = File(context.cacheDir, "updates")

  val currentVersionName: String get() = BuildConfig.VERSION_NAME
  val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
  /** Debug builds are signed with a developer key, so a published release can't be installed over them. */
  val isDevelopmentBuild: Boolean get() = BuildConfig.DEBUG

  /** The latest release if it is newer than this app, else null. Throws [UpdateException] when it can't be checked. */
  suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
    val req = Request.Builder().url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
      .header("Accept", "application/vnd.github+json").header("User-Agent", "upi-manager-android").build()
    val json = try {
      http.newCall(req).execute().use { r ->
        if (r.code == 404) return@withContext null // nothing published yet
        if (!r.isSuccessful) throw UpdateException("Couldn't check for updates right now. Please try again later.")
        JSONObject(r.body?.string().orEmpty())
      }
    } catch (e: IOException) {
      throw UpdateException("You appear to be offline. Check your connection and try again.")
    }
    if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null
    val body = json.optString("body")
    val code = Regex("""versionCode=(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext null
    if (code <= BuildConfig.VERSION_CODE) return@withContext null
    val assets = json.optJSONArray("assets") ?: return@withContext null
    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull {
      it.optString("name").endsWith(".apk") && it.optString("browser_download_url").startsWith("https://github.com/")
    } ?: return@withContext null
    AppUpdate(
      versionCode = code,
      versionName = json.optString("tag_name").removePrefix("v"),
      notes = body.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "").trim(),
      apkUrl = apk.getString("browser_download_url"),
      sizeBytes = apk.optLong("size"),
      sha256 = Regex("""sha256=([0-9a-f]{64})""").find(body)?.groupValues?.get(1)
    )
  }

  /** Downloads the APK (reporting 0..1 progress) and checks its SHA-256. Re-uses an earlier complete download. */
  suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
    dir.mkdirs()
    val target = File(dir, "upi-manager-${update.versionCode}.apk")
    if (target.exists() && update.sha256 != null && sha256(target) == update.sha256) return@withContext target
    dir.listFiles()?.forEach { it.delete() } // older downloads
    val part = File(dir, target.name + ".part")
    try {
      http.newCall(Request.Builder().url(update.apkUrl).header("User-Agent", "upi-manager-android").build()).execute().use { r ->
        val body = r.body
        if (!r.isSuccessful || body == null) throw UpdateException("The update couldn't be downloaded. Please try again.")
        val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
        body.byteStream().use { input ->
          part.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            var read = 0L
            while (true) {
              val n = input.read(buf)
              if (n < 0) break
              out.write(buf, 0, n)
              read += n
              if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
            }
          }
        }
      }
    } catch (e: IOException) {
      part.delete()
      throw UpdateException("The download was interrupted. Check your connection and try again.")
    }
    if (update.sha256 != null && sha256(part) != update.sha256) {
      part.delete()
      throw UpdateException("The downloaded update was damaged, so it wasn't installed. Please try again.")
    }
    if (!part.renameTo(target)) throw UpdateException("The update couldn't be saved. Free some space and try again.")
    target
  }

  /** True when Android lets this app open the installer (the user allows it once in system settings). */
  fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

  /** Opens the system screen where the user allows this app to install updates. */
  fun openInstallPermission() {
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
  }

  /** Hands the APK to Android's package installer, which asks the user to confirm. */
  fun install(apk: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }

  private fun sha256(f: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { input ->
      val buf = ByteArray(64 * 1024)
      while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }
}
