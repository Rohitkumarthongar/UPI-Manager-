package com.example.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BudgetGoal
import com.example.data.model.RecurringBill
import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import com.example.ui.components.FintechCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.StatusTag
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.util.ReportExporter
import java.util.Locale

@Composable
fun ReportsScreen(
  transactions: List<TransactionItem>,
  recurringBills: List<RecurringBill>,
  budgets: List<BudgetGoal>,
  upiAccounts: List<UpiAccount>,
  isDarkMode: Boolean,
  onToggleDarkMode: () -> Unit,
  onCheckUpdate: () -> Unit,
  onPayBill: (RecurringBill, Long?) -> Unit,
  onAddBill: (title: String, amount: Double, dueDay: Int, category: String, upiId: Long?) -> Unit,
  onDeleteBill: (RecurringBill) -> Unit,
  onSetBudget: (category: String, limit: Double) -> Unit,
  onDeleteBudget: (BudgetGoal) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var showAddBillDialog by remember { mutableStateOf(false) }
  var showSetBudgetDialog by remember { mutableStateOf(false) }

  val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
  val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
  val netProfit = totalIncome - totalExpense
  val savingsRate = if (totalIncome > 0) ((netProfit / totalIncome) * 100).toInt() else 0

  val expensesByCategory = transactions
    .filter { it.type == "EXPENSE" }
    .groupBy { it.category }
    .mapValues { entry -> entry.value.sumOf { it.amount } }
    .toList()
    .sortedByDescending { it.second }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("reports_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // 1. Monthly Financial Executive Summary
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
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
                text = "Automated Monthly Report",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
              Text(
                text = "September 2026 Audit Summary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
              )
            }
            StatusTag(
              text = "Savings: $savingsRate%",
              color = if (savingsRate >= 0) EmeraldDark else AccentRose
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text("Total Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
              Text(formatCurrency(totalIncome), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column {
              Text("Total Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
              Text(formatCurrency(totalExpense), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column {
              Text("Net Surplus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
              Text(formatCurrency(netProfit), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = if (netProfit >= 0) EmeraldDark else AccentRose)
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Export Actions
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedButton(
              onClick = {
                val csv = ReportExporter.generateTransactionsCsv(transactions, "September_2026")
                ReportExporter.shareReport(context, csv, "Monthly_Report_Sep2026", "csv", "text/csv")
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).testTag("export_monthly_csv")
            ) {
              Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Export CSV")
            }

            Button(
              onClick = {
                val printable = StringBuilder().apply {
                  append("==============================================\n")
                  append("      MONTHLY FINANCIAL REPORT - OFFLINE LEDGER\n")
                  append("==============================================\n")
                  append("Period: September 2026\n")
                  append("Generated: ${java.util.Date()}\n\n")
                  append(String.format(Locale.US, "Total Income   : ₹ %,.2f\n", totalIncome))
                  append(String.format(Locale.US, "Total Expense  : ₹ %,.2f\n", totalExpense))
                  append(String.format(Locale.US, "Net Profit/Loss: ₹ %,.2f\n", netProfit))
                  append(String.format(Locale.US, "Savings Rate   : %d%%\n\n", savingsRate))
                  append("EXPENSES BY CATEGORY:\n")
                  expensesByCategory.forEach { (cat, amt) ->
                    val pct = if (totalExpense > 0) (amt / totalExpense * 100).toInt() else 0
                    append(String.format(Locale.US, " - %-14s: ₹ %,.2f (%d%%)\n", cat, amt, pct))
                  }
                  append("==============================================\n")
                  append("Certified Offline Record (AES-256 Keystore Protected)\n")
                }.toString()

                ReportExporter.shareReport(context, printable, "Monthly_Audit_Sep2026", "txt", "text/plain")
              },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier.weight(1f).testTag("export_monthly_pdf")
            ) {
              Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Export PDF/Doc")
            }
          }
        }
      }
    }

    // 2. Spending by Category Breakdown
    item {
      FintechCard(modifier = Modifier.fillMaxWidth()) {
        Column {
          Text(
            text = "Expense Distribution by Category",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          Spacer(modifier = Modifier.height(12.dp))

          if (expensesByCategory.isEmpty()) {
            Text("No expenses logged yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              expensesByCategory.forEach { (cat, amt) ->
                val fraction = if (totalExpense > 0) (amt / totalExpense).toFloat() else 0f
                Column {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(cat, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                      text = "${formatCurrency(amt)} (${(fraction * 100).toInt()}%)",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(6.dp)
                      .clip(RoundedCornerShape(3.dp)),
                    color = AccentIndigo,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                  )
                }
              }
            }
          }
        }
      }
    }

    // 3. Recurring Monthly Bills Management
    item {
      FintechCard(modifier = Modifier.fillMaxWidth()) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Recurring Bills & Reminders",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Auto-settle to ledger with 1 tap",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            TextButton(onClick = { showAddBillDialog = true }) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add Bill")
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          if (recurringBills.isEmpty()) {
            Text("No recurring bills added.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              recurringBills.forEach { bill ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column {
                    Text(bill.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                      text = "Due day ${bill.dueDayOfMonth} • ${formatCurrency(bill.amount)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                      onClick = { onPayBill(bill, upiAccounts.firstOrNull()?.id) },
                      shape = RoundedCornerShape(8.dp),
                      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                      modifier = Modifier.height(32.dp)
                    ) {
                      Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Pay Now", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { onDeleteBill(bill) }, modifier = Modifier.size(28.dp)) {
                      Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // 4. Monthly Budget Limits
    item {
      FintechCard(modifier = Modifier.fillMaxWidth()) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Budget Limits & Alerts",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Track thresholds per category",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            TextButton(onClick = { showSetBudgetDialog = true }) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Set Limit")
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            budgets.forEach { budget ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant)
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(budget.category, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(formatCurrency(budget.monthlyLimit), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                  IconButton(onClick = { onDeleteBudget(budget) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                  }
                }
              }
            }
          }
        }
      }
    }

    // 5. Settings: Dark Mode Toggle & Offline Security
    item {
      FintechCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text("App Settings & Privacy", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Dark Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("Optimized for late-night bookkeeping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Switch(
              checked = isDarkMode,
              onCheckedChange = { onToggleDarkMode() },
              modifier = Modifier.testTag("dark_mode_switch")
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Sync, contentDescription = null, tint = EmeraldDark)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Device Synchronization", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("Offline-first local Room with SAF export", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            StatusTag(text = "Active", color = EmeraldDark)
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Check for App Updates", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("Version v1.0.0 • Firebase Distribution", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            OutlinedButton(
              onClick = { onCheckUpdate() },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.testTag("check_updates_button")
            ) {
              Text("Check Now")
            }
          }
        }
      }
    }
  }

  // Add Recurring Bill Dialog
  if (showAddBillDialog) {
    var billTitle by remember { mutableStateOf("") }
    var billAmount by remember { mutableStateOf("") }
    var billDay by remember { mutableStateOf("1") }
    var billCategory by remember { mutableStateOf("Utilities") }

    AlertDialog(
      onDismissRequest = { showAddBillDialog = false },
      title = { Text("Add Recurring Monthly Bill") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = billTitle,
            onValueChange = { billTitle = it },
            label = { Text("Bill Name *") },
            placeholder = { Text("e.g. Office Rent, Cloud Server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = billAmount,
            onValueChange = { billAmount = it },
            label = { Text("Amount (₹) *") },
            placeholder = { Text("0.00") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = billDay,
            onValueChange = { billDay = it },
            label = { Text("Due Day of Month (1 - 31)") },
            placeholder = { Text("1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = billCategory,
            onValueChange = { billCategory = it },
            label = { Text("Category") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val amt = billAmount.toDoubleOrNull() ?: 0.0
            val day = billDay.toIntOrNull() ?: 1
            if (billTitle.isNotBlank() && amt > 0) {
              onAddBill(billTitle, amt, day, billCategory, upiAccounts.firstOrNull()?.id)
              showAddBillDialog = false
            }
          },
          enabled = billTitle.isNotBlank() && (billAmount.toDoubleOrNull() ?: 0.0) > 0
        ) {
          Text("Save Bill")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddBillDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Set Budget Dialog
  if (showSetBudgetDialog) {
    var budgetCategory by remember { mutableStateOf("") }
    var budgetLimit by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showSetBudgetDialog = false },
      title = { Text("Set Monthly Budget Goal") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = budgetCategory,
            onValueChange = { budgetCategory = it },
            label = { Text("Category Name *") },
            placeholder = { Text("e.g. Inventory, Supplies, Travel") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = budgetLimit,
            onValueChange = { budgetLimit = it },
            label = { Text("Monthly Limit (₹) *") },
            placeholder = { Text("e.g. 25000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val limit = budgetLimit.toDoubleOrNull() ?: 0.0
            if (budgetCategory.isNotBlank() && limit > 0) {
              onSetBudget(budgetCategory, limit)
              showSetBudgetDialog = false
            }
          },
          enabled = budgetCategory.isNotBlank() && (budgetLimit.toDoubleOrNull() ?: 0.0) > 0
        ) {
          Text("Set Budget")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSetBudgetDialog = false }) { Text("Cancel") }
      }
    )
  }
}
