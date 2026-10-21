package com.example.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** How often the app looks for an update on its own (on launch and when it comes back to the foreground). */
private const val AUTO_CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

/**
 * App updates: the automatic check with its pop-up, and the "Check for updates" button in Reports → App Settings.
 * Download → SHA-256 check → Android's installer. The first time, Android asks the user to allow this app to
 * install updates; the install continues when they come back.
 */
@Stable
class UpdateController(private val updater: AppUpdater, private val scope: CoroutineScope, private val toast: (String) -> Unit) {
  var update by mutableStateOf<AppUpdate?>(null); private set
  var showDialog by mutableStateOf(false); private set
  /** 0..1 while downloading, null otherwise. */
  var progress by mutableStateOf<Float?>(null); private set
  var checking by mutableStateOf(false); private set
  /** The update is downloaded but Android needs the user to allow installs from this app first. */
  var needsPermission by mutableStateOf(false); private set

  val versionName get() = updater.currentVersionName
  val isDevelopmentBuild get() = updater.isDevelopmentBuild

  private var downloaded: File? = null
  private var lastAutoCheck = 0L
  private val postponed = mutableSetOf<Int>()

  /** Quiet check: shows the pop-up only when there is something new the user hasn't postponed. */
  fun autoCheck() {
    // A development build can't be updated by a published release (different signing key): don't nag.
    if (updater.isDevelopmentBuild || checking || progress != null) return
    val now = System.currentTimeMillis()
    if (now - lastAutoCheck < AUTO_CHECK_EVERY_MS) return
    lastAutoCheck = now
    scope.launch {
      val found = runCatching { updater.check() }.getOrNull() ?: return@launch
      update = found
      if (found.versionCode !in postponed) showDialog = true
    }
  }

  /** "Check for updates": always answers, with the pop-up or a message. */
  fun manualCheck() {
    if (checking) return
    checking = true
    scope.launch {
      try {
        val found = updater.check()
        update = found
        when {
          found == null -> toast("You have the latest version ($versionName).")
          updater.isDevelopmentBuild -> {
            toast("Version ${found.versionName} is available, but this is a debug build. Install the release APK once to get updates.")
            showDialog = true
          }
          else -> showDialog = true
        }
      } catch (e: UpdateException) {
        toast(e.message ?: "Couldn't check for updates.")
      } finally {
        checking = false
      }
    }
  }

  fun later() {
    update?.let { postponed += it.versionCode }
    showDialog = false
  }

  fun install() {
    val u = update ?: return
    if (progress != null) return
    downloaded?.takeIf { it.exists() }?.let { return handOver(it) }
    progress = 0f
    scope.launch {
      try {
        val file = updater.download(u) { p -> progress = p }
        downloaded = file
        handOver(file)
      } catch (e: UpdateException) {
        toast(e.message ?: "The update couldn't be downloaded.")
      } finally {
        progress = null
      }
    }
  }

  fun allowInstalls() = updater.openInstallPermission()

  /** Back from system settings: carry on with the install if the user allowed it. */
  fun onResume() {
    val file = downloaded
    if (needsPermission && file != null && updater.canInstall()) handOver(file) else autoCheck()
  }

  private fun handOver(file: File) {
    if (!updater.canInstall()) {
      needsPermission = true
      showDialog = true
      return
    }
    needsPermission = false
    showDialog = false
    runCatching { updater.install(file) }.onFailure { toast("Couldn't open the installer. Please try again.") }
  }
}

@Composable
fun UpdateDialog(c: UpdateController) {
  val u = c.update ?: return
  if (!c.showDialog) return
  val downloading = c.progress != null
  AlertDialog(
    onDismissRequest = { if (!downloading) c.later() },
    title = { Text(if (c.needsPermission) "Allow app updates" else "Update available") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
          c.needsPermission -> Text("To install version ${u.versionName}, allow \"Install unknown apps\" for UPI Manager on the next screen, then come back.")
          else -> {
            Text("Version ${u.versionName} is ready (you have ${c.versionName}).")
            if (u.notes.isNotBlank()) Text(u.notes, style = MaterialTheme.typography.bodySmall)
            if (u.sizeBytes > 0) {
              Text(
                "Download size ${"%.1f".format(u.sizeBytes / 1_048_576.0)} MB",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
        c.progress?.let { p ->
          LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
          Text("Downloading… ${(p * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
      }
    },
    confirmButton = {
      if (c.needsPermission) TextButton(onClick = c::allowInstalls) { Text("Allow") }
      else TextButton(enabled = !downloading, onClick = c::install) { Text(if (downloading) "Downloading…" else "Update now") }
    },
    dismissButton = { if (!downloading) TextButton(onClick = c::later) { Text("Later") } }
  )
}
