package com.example.ui.dialogs

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.model.NotificationDraft
import com.example.ui.components.EmptyStateView
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationInboxDialog(
  drafts: List<NotificationDraft>,
  onConfirmDraft: (NotificationDraft) -> Unit,
  onDismissDraft: (String) -> Unit,
  onSyncSmsInbox: () -> Unit,
  onClose: () -> Unit
) {
  val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .padding(vertical = 24.dp)
        .testTag("notification_inbox_dialog")
    ) {
      Column(modifier = Modifier.padding(20.dp)) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(EmeraldPrimary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.NotificationsActive,
                contentDescription = "Inbox",
                tint = EmeraldDark,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Payment Confirmations",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Review draft entries before posting",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (drafts.isEmpty()) {
          EmptyStateView(
            icon = Icons.Default.Payments,
            title = "Inbox is Empty",
            subtitle = "No unconfirmed payment alerts.\nBank SMS is scanned with permission; you can also import manually."
          )
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f, fill = false)
          ) {
            items(drafts, key = { it.id }) { draft ->
              DraftItemCard(
                draft = draft,
                timeStr = timeFormat.format(Date(draft.timestamp)),
                onConfirm = { onConfirmDraft(draft) },
                onDismiss = { onDismissDraft(draft.id) }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SMS Inbox Sync Button
        OutlinedButton(
          onClick = onSyncSmsInbox,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_sms_inbox_button")
        ) {
          Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Scan Bank SMS Inbox for Payments", style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@Composable
fun DraftItemCard(
  draft: NotificationDraft,
  timeStr: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  val isIncome = draft.type == "INCOME"
  val amountColor = if (isIncome) EmeraldDark else AccentRose

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
          ) {
            Text(
              text = draft.senderApp,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = timeStr,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Text(
          text = "${if (isIncome) "+" else "-"} ${formatCurrency(draft.amount)}",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = amountColor
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = draft.rawText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
      )

      if (draft.upiReference != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Ref: ${draft.upiReference}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.height(36.dp)
        ) {
          Text("Discard", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = onConfirm,
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier.height(36.dp)
        ) {
          Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Confirm to Ledger", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}
