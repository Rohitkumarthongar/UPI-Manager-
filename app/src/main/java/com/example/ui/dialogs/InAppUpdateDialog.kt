package com.example.ui.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.util.AppVersionConfig

@Composable
fun InAppUpdateDialog(
  config: AppVersionConfig,
  isDownloading: Boolean,
  downloadProgress: Int,
  onStartUpdate: () -> Unit,
  onDismiss: () -> Unit
) {
  // If update is mandatory, prevent back button dismiss
  if (config.isMandatory) {
    BackHandler(enabled = true) { /* Block back press */ }
  }

  Dialog(
    onDismissRequest = {
      if (!config.isMandatory && !isDownloading) {
        onDismiss()
      }
    },
    properties = DialogProperties(
      dismissOnBackPress = !config.isMandatory && !isDownloading,
      dismissOnClickOutside = !config.isMandatory && !isDownloading,
      usePlatformDefaultWidth = false
    )
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .padding(vertical = 16.dp)
        .testTag("in_app_update_dialog")
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Icon & Version Badge Header
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(EmeraldPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.SystemUpdate,
              contentDescription = "Update Available",
              tint = EmeraldDark,
              modifier = Modifier.size(26.dp)
            )
          }
          Spacer(modifier = Modifier.width(14.dp))
          Column {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = EmeraldPrimary.copy(alpha = 0.2f)
            ) {
              Text(
                text = if (config.isMandatory) "CRITICAL UPDATE" else "NEW VERSION v${config.latestVersionName}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = EmeraldDark,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Update Available",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Release Notes
        Text(
          text = "What's New:",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = config.releaseNotes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(14.dp)
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Download Progress Indicator
        if (isDownloading) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Downloading APK update...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "$downloadProgress%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = EmeraldDark
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
              progress = { downloadProgress / 100f },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
              color = EmeraldPrimary
            )
          }
          Spacer(modifier = Modifier.height(20.dp))
        }

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (!config.isMandatory && !isDownloading) {
            OutlinedButton(
              onClick = onDismiss,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.padding(end = 8.dp)
            ) {
              Text("Later")
            }
          }

          Button(
            onClick = onStartUpdate,
            enabled = !isDownloading,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("update_now_button")
          ) {
            Text(
              text = if (isDownloading) "Downloading..." else "Update Now",
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}
