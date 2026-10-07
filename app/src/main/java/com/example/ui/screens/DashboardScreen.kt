package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetGoal
import com.example.data.model.NotificationDraft
import com.example.data.model.RecurringBill
import com.example.data.model.TaskItem
import com.example.data.model.TransactionItem
import com.example.sync.ConnectedClientInfo
import com.example.ui.components.FintechCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.StatusTag
import com.example.ui.components.SyncStatusGlanceCard
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary

@Composable
fun DashboardScreen(
  transactions: List<TransactionItem>,
  tasks: List<TaskItem>,
  recurringBills: List<RecurringBill>,
  budgets: List<BudgetGoal>,
  notificationDrafts: List<NotificationDraft>,
  isHostingSync: Boolean = false,
  connectedClients: List<ConnectedClientInfo> = emptyList(),
  isSyncing: Boolean = false,
  lastSyncSummary: String = "",
  lastSyncTime: Long? = null,
  clientHostUrl: String = "",
  customDeviceName: String = "",
  onTriggerSync: () -> Unit = {},
  onOpenSyncScreen: () -> Unit = {},
  onOpenReceiveUpi: () -> Unit,
  onOpenOcrScanner: () -> Unit,
  onOpenAddTransaction: () -> Unit,
  onOpenNotificationInbox: () -> Unit,
  onPayBill: (RecurringBill) -> Unit,
  onToggleTaskDone: (TaskItem) -> Unit,
  onViewAllTasks: () -> Unit,
  onViewAllReports: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Financial computations
  val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
  val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
  val netSavings = totalIncome - totalExpense

  // Category spending aggregation
  val categorySpending = transactions
    .filter { it.type == "EXPENSE" }
    .groupBy { it.category }
    .mapValues { entry -> entry.value.sumOf { it.amount } }

  // Task progress calculation
  val completedTasksCount = tasks.count { it.isDone }
  val taskProgress = if (tasks.isNotEmpty()) completedTasksCount.toFloat() / tasks.size else 0f

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("dashboard_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Hero Balance Overview Card
    item {
      HeroBalanceCard(
        netSavings = netSavings,
        totalIncome = totalIncome,
        totalExpense = totalExpense,
        onReceiveUpi = onOpenReceiveUpi,
        onAddTxn = onOpenAddTransaction
      )
    }

    // 2. Multi-Device Peer Sync Status & Pending Updates Card
    item {
      SyncStatusGlanceCard(
        isHosting = isHostingSync,
        connectedClients = connectedClients,
        isSyncing = isSyncing,
        lastSyncSummary = lastSyncSummary,
        lastSyncTime = lastSyncTime,
        clientHostUrl = clientHostUrl,
        customDeviceName = customDeviceName,
        onTriggerSync = onTriggerSync,
        onOpenSyncScreen = onOpenSyncScreen
      )
    }

    // 3. Quick Action Shortcuts Bar
    item {
      QuickActionsRow(
        draftsCount = notificationDrafts.size,
        onOpenReceiveUpi = onOpenReceiveUpi,
        onOpenOcrScanner = onOpenOcrScanner,
        onOpenNotificationInbox = onOpenNotificationInbox,
        onOpenAddTxn = onOpenAddTransaction
      )
    }

    // 3. Daily Tasks Productivity Glance
    item {
      DailyTasksGlanceCard(
        tasks = tasks,
        completedCount = completedTasksCount,
        progress = taskProgress,
        onToggleTask = onToggleTaskDone,
        onViewAll = onViewAllTasks
      )
    }

    // 4. Monthly Budget Goals & Spending Alerts
    item {
      BudgetTrackingCard(
        budgets = budgets,
        categorySpending = categorySpending,
        onViewReports = onViewAllReports
      )
    }

    // 5. Upcoming Recurring Bills Card
    item {
      UpcomingBillsCard(
        bills = recurringBills,
        onPayBill = onPayBill
      )
    }

    // 6. Security & Offline Guarantee Badge
    item {
      OfflineSecurityBadge()
    }
  }
}

@Composable
fun HeroBalanceCard(
  netSavings: Double,
  totalIncome: Double,
  totalExpense: Double,
  onReceiveUpi: () -> Unit,
  onAddTxn: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Net Cash Flow",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
          )
          Text(
            text = formatCurrency(netSavings),
            style = MaterialTheme.typography.headlineLarge.copy(
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = (-1).sp
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
        StatusTag(
          text = if (netSavings > 0) "Surplus" else if (netSavings < 0) "Deficit" else "No activity",
          color = if (netSavings >= 0) EmeraldDark else AccentRose
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Mini Income & Expense Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(EmeraldPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(16.dp))
          }
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Text(formatCurrency(totalIncome), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(AccentRose.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = AccentRose, modifier = Modifier.size(16.dp))
          }
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text("Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Text(formatCurrency(totalExpense), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Quick Receive / Pay Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Button(
          onClick = onReceiveUpi,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier.weight(1f).testTag("hero_receive_upi_button")
        ) {
          Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Receive UPI", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
          onClick = onAddTxn,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).testTag("hero_log_txn_button")
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Log Txn", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
fun QuickActionsRow(
  draftsCount: Int,
  onOpenReceiveUpi: () -> Unit,
  onOpenOcrScanner: () -> Unit,
  onOpenNotificationInbox: () -> Unit,
  onOpenAddTxn: () -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    QuickActionTile(
      title = "Scan OCR",
      subtitle = "Receipt bill",
      icon = Icons.Default.DocumentScanner,
      color = AccentIndigo,
      modifier = Modifier.weight(1f),
      onClick = onOpenOcrScanner
    )
    QuickActionTile(
      title = "Pay Inbox",
      subtitle = if (draftsCount > 0) "$draftsCount unposted" else "No drafts",
      icon = Icons.Default.NotificationsActive,
      color = if (draftsCount > 0) AccentAmber else EmeraldPrimary,
      badgeCount = draftsCount,
      modifier = Modifier.weight(1f),
      onClick = onOpenNotificationInbox
    )
    QuickActionTile(
      title = "UPI QR",
      subtitle = "Multi-account",
      icon = Icons.Default.QrCodeScanner,
      color = AccentCyan,
      modifier = Modifier.weight(1f),
      onClick = onOpenReceiveUpi
    )
  }
}

@Composable
fun QuickActionTile(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  color: Color,
  modifier: Modifier = Modifier,
  badgeCount: Int = 0,
  onClick: () -> Unit
) {
  FintechCard(
    modifier = modifier,
    onClick = onClick
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth()
    ) {
      BadgedBox(
        badge = {
          if (badgeCount > 0) {
            Badge(containerColor = AccentRose) { Text("$badgeCount") }
          }
        }
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(imageVector = icon, contentDescription = title, tint = color, modifier = Modifier.size(20.dp))
        }
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun DailyTasksGlanceCard(
  tasks: List<TaskItem>,
  completedCount: Int,
  progress: Float,
  onToggleTask: (TaskItem) -> Unit,
  onViewAll: () -> Unit
) {
  FintechCard(modifier = Modifier.fillMaxWidth()) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(EmeraldPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(18.dp))
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Daily Tasks & Reminders",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "$completedCount of ${tasks.size} done today",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        CircularProgressIndicator(
          progress = { progress },
          modifier = Modifier.size(36.dp),
          color = EmeraldPrimary,
          trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
          strokeWidth = 3.5.dp
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Top 3 tasks preview
      val previewTasks = tasks.take(3)
      if (previewTasks.isEmpty()) {
        Text("No active tasks today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          previewTasks.forEach { task ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                  onClick = { onToggleTask(task) },
                  modifier = Modifier.size(24.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Toggle",
                    tint = if (task.isDone) EmeraldDark else MaterialTheme.colorScheme.outline
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = task.title,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (task.isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                  ),
                  color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                  maxLines = 1
                )
              }
              if (task.streakCount > 0) {
                StatusTag(text = "🔥 ${task.streakCount}d", color = AccentAmber)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        OutlinedButton(
          onClick = onViewAll,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.height(32.dp)
        ) {
          Text("Manage Tasks", style = MaterialTheme.typography.labelSmall)
          Spacer(modifier = Modifier.width(4.dp))
          Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
        }
      }
    }
  }
}

@Composable
fun BudgetTrackingCard(
  budgets: List<BudgetGoal>,
  categorySpending: Map<String, Double>,
  onViewReports: () -> Unit
) {
  FintechCard(modifier = Modifier.fillMaxWidth()) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Monthly Budgets & Goals",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Spending limits and over-budget alerts",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(onClick = onViewReports) {
          Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Reports")
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (budgets.isEmpty()) {
        Text("No budget goals configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          budgets.take(4).forEach { budget ->
            val spent = categorySpending[budget.category] ?: 0.0
            val fraction = (spent / budget.monthlyLimit).coerceIn(0.0, 1.0).toFloat()
            val isExceeded = spent > budget.monthlyLimit
            val statusColor = when {
              isExceeded -> AccentRose
              fraction > 0.8f -> AccentAmber
              else -> EmeraldPrimary
            }

            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = budget.category,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  if (isExceeded) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Warning, contentDescription = "Exceeded", tint = AccentRose, modifier = Modifier.size(14.dp))
                  }
                }
                Text(
                  text = "${formatCurrency(spent)} / ${formatCurrency(budget.monthlyLimit)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = statusColor
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun UpcomingBillsCard(
  bills: List<RecurringBill>,
  onPayBill: (RecurringBill) -> Unit
) {
  FintechCard(modifier = Modifier.fillMaxWidth()) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(AccentAmber.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Upcoming Recurring Bills",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Auto-settle to ledger with 1 tap",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (bills.isEmpty()) {
        Text("No recurring bills scheduled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          bills.take(3).forEach { bill ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text(bill.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("Due: Day ${bill.dueDayOfMonth} of month • ${formatCurrency(bill.amount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Button(
                onClick = { onPayBill(bill) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(32.dp)
              ) {
                Text("Mark Paid", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun OfflineSecurityBadge() {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(EmeraldPrimary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = "Local ledger",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "Review and export your locally stored ledger records.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
