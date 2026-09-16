package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sync.ConnectedClientInfo
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-visibility Sync Status UI Component on the Dashboard.
 * Displays connected peer devices, live connection health, last synchronized ledger update,
 * and gives the operator one-tap triggers to check for pending ledger updates or refresh sync.
 */
@Composable
fun SyncStatusGlanceCard(
  isHosting: Boolean,
  connectedClients: List<ConnectedClientInfo>,
  isSyncing: Boolean,
  lastSyncSummary: String,
  lastSyncTime: Long?,
  clientHostUrl: String,
  customDeviceName: String,
  onTriggerSync: () -> Unit,
  onOpenSyncScreen: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showDetails by remember { mutableStateOf(false) }

  val connectedPeerCount = if (isHosting) {
    connectedClients.size
  } else if (clientHostUrl.isNotBlank() && (lastSyncTime ?: 0L) > 0) {
    1
  } else {
    0
  }

  val isOnline = isHosting || (clientHostUrl.isNotBlank() && (lastSyncTime ?: 0L) > 0)

  // Pulsing animation for active sync status indicator
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.25f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  FintechCard(
    modifier = modifier
      .fillMaxWidth()
      .testTag("dashboard_sync_status_card")
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Top Row: Status badge, mode indicator, and settings trigger
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(
                if (isHosting) EmeraldPrimary.copy(alpha = 0.15f)
                else if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isHosting) Icons.Default.WifiTethering else Icons.Default.Sync,
              contentDescription = null,
              tint = if (isHosting) EmeraldDark
              else if (isOnline) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(10.dp))

          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Wi-Fi Multi-Device Sync",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.width(6.dp))
              // Pulse indicator dot
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .scale(if (isOnline) pulseScale else 1f)
                  .clip(CircleShape)
                  .background(
                    if (isSyncing) AccentAmber
                    else if (isOnline) EmeraldPrimary
                    else MaterialTheme.colorScheme.outline
                  )
              )
            }

            Text(
              text = if (customDeviceName.isNotBlank()) "This Mobile: $customDeviceName" else "Offline Local Range Network",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          val modeTagText = when {
            isHosting -> "HUB HOST"
            clientHostUrl.isNotBlank() -> "PEER CLIENT"
            else -> "STANDBY"
          }
          val modeTagColor = when {
            isHosting -> EmeraldDark
            clientHostUrl.isNotBlank() -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
          }
          StatusTag(text = modeTagText, color = modeTagColor)

          IconButton(
            onClick = onOpenSyncScreen,
            modifier = Modifier.size(32.dp).testTag("btn_dashboard_open_sync_screen")
          ) {
            Icon(
              Icons.Default.Settings,
              contentDescription = "Open Multi-Device Sync Setup",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      // Middle Banner: Number of Connected Peer Devices & Network Glance
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isOnline) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = if (connectedPeerCount > 0) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = when {
                    isHosting -> "$connectedPeerCount Connected Peer Device${if (connectedPeerCount == 1) "" else "s"}"
                    clientHostUrl.isNotBlank() -> if (connectedPeerCount > 0) "Linked to Hub Peer" else "Configured (Awaiting Hub)"
                    else -> "No Peer Devices Connected"
                  },
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = when {
                    isHosting && connectedClients.isNotEmpty() -> "Online peers: ${connectedClients.joinToString { it.deviceName }}"
                    isHosting -> "Hub active & listening on Wi-Fi for Mobile 1 & 2"
                    clientHostUrl.isNotBlank() -> "Host URL: $clientHostUrl"
                    else -> "Connect with up to 3 mobiles on same Wi-Fi/Hotspot"
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (isHosting && connectedClients.isNotEmpty()) {
              Surface(
                shape = CircleShape,
                color = EmeraldPrimary.copy(alpha = 0.2f),
                modifier = Modifier.padding(2.dp)
              ) {
                Text(
                  text = "${connectedClients.size} ACTIVE",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
                  color = EmeraldDark,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
              }
            }
          }

          // Chips for individual connected clients if hosting
          if (isHosting && connectedClients.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              items(connectedClients) { client ->
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                  modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "${client.deviceName} (${client.ipAddress})",
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Ledger Synchronization Status Line
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = if (isSyncing) Icons.Default.Sync else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isSyncing) AccentAmber else EmeraldDark,
            modifier = Modifier.size(15.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = when {
              isSyncing -> "Syncing ledger with peer devices..."
              lastSyncSummary.isNotBlank() -> lastSyncSummary
              (lastSyncTime ?: 0L) > 0L -> "All ledger transactions synchronized"
              else -> "Ready to sync with operator mobiles"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
          )
        }

        Text(
          text = formatRelativeSyncTime(lastSyncTime),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = MaterialTheme.colorScheme.outline
        )
      }

      // Action Buttons: Manual Sync Trigger & Pending Updates Check
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = onTriggerSync,
          enabled = !isSyncing,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isHosting) EmeraldPrimary else MaterialTheme.colorScheme.primary
          ),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
          modifier = Modifier
            .weight(1.3f)
            .height(42.dp)
            .testTag("btn_dashboard_manual_sync")
        ) {
          if (isSyncing) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Checking...", style = MaterialTheme.typography.labelMedium)
          } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isHosting) "Check Peer Updates" else "Sync Refresh",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
          }
        }

        OutlinedButton(
          onClick = onOpenSyncScreen,
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier
            .weight(1f)
            .height(42.dp)
            .testTag("btn_dashboard_open_sync")
        ) {
          Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Sync Hub", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

private fun formatRelativeSyncTime(timestamp: Long?): String {
  if (timestamp == null || timestamp <= 0L) return "Never synced"
  val diff = System.currentTimeMillis() - timestamp
  return when {
    diff < 30_000L -> "Just now"
    diff < 60_000L -> "${diff / 1000}s ago"
    diff < 3600_000L -> "${diff / 60_000L}m ago"
    else -> SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
  }
}
