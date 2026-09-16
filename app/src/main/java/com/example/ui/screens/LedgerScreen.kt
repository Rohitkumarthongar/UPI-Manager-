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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Client
import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import com.example.ui.components.EmptyStateView
import com.example.ui.components.StatusTag
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LedgerScreen(
  transactions: List<TransactionItem>,
  upiAccounts: List<UpiAccount>,
  clients: List<Client>,
  draftsCount: Int,
  typeFilter: String,
  searchQuery: String,
  selectedAccountId: Long?,
  onTypeFilterChange: (String) -> Unit,
  onSearchChange: (String) -> Unit,
  onAccountFilterChange: (Long?) -> Unit,
  onOpenAddTransaction: () -> Unit,
  onOpenOcrScanner: () -> Unit,
  onOpenNotificationInbox: () -> Unit,
  onDeleteTransaction: (TransactionItem) -> Unit,
  modifier: Modifier = Modifier
) {
  var transactionToDelete by remember { mutableStateOf<TransactionItem?>(null) }
  val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

  // Filtering logic
  val filteredTransactions = transactions.filter { txn ->
    val matchesType = when (typeFilter) {
      "INCOME" -> txn.type == "INCOME"
      "EXPENSE" -> txn.type == "EXPENSE"
      else -> true
    }
    val matchesAccount = selectedAccountId == null || txn.accountId == selectedAccountId
    val matchesSearch = searchQuery.isBlank() ||
      txn.note.contains(searchQuery, ignoreCase = true) ||
      txn.category.contains(searchQuery, ignoreCase = true) ||
      (txn.vendorName?.contains(searchQuery, ignoreCase = true) == true) ||
      txn.amount.toString().contains(searchQuery)

    matchesType && matchesAccount && matchesSearch
  }

  Box(modifier = modifier.fillMaxSize().testTag("ledger_screen")) {
    Column(modifier = Modifier.fillMaxSize()) {
      // 1. Search Bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Search transactions, vendors, notes...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchChange("") }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
          .testTag("ledger_search_field")
      )

      // 2. Filter Chips (All, Income, Expense)
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        item {
          FilterChip(
            selected = typeFilter == "ALL",
            onClick = { onTypeFilterChange("ALL") },
            label = { Text("All (${transactions.size})") }
          )
        }
        item {
          FilterChip(
            selected = typeFilter == "INCOME",
            onClick = { onTypeFilterChange("INCOME") },
            label = { Text("Income") }
          )
        }
        item {
          FilterChip(
            selected = typeFilter == "EXPENSE",
            onClick = { onTypeFilterChange("EXPENSE") },
            label = { Text("Expense") }
          )
        }
        if (upiAccounts.isNotEmpty()) {
          items(upiAccounts) { acc ->
            FilterChip(
              selected = selectedAccountId == acc.id,
              onClick = {
                onAccountFilterChange(if (selectedAccountId == acc.id) null else acc.id)
              },
              label = { Text(acc.label) }
            )
          }
        }
      }

      // 3. Notification confirmation banner if drafts exist
      if (draftsCount > 0) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = AccentAmber.copy(alpha = 0.15f),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "$draftsCount unposted payment confirmations",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            TextButton(onClick = onOpenNotificationInbox) {
              Text("Review Inbox", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // 4. Transactions List
      if (filteredTransactions.isEmpty()) {
        EmptyStateView(
          icon = Icons.Default.ReceiptLong,
          title = "No Transactions Found",
          subtitle = "Tap + to log your daily business income or expenses, or scan a receipt via OCR."
        )
      } else {
        LazyColumn(
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(filteredTransactions, key = { it.id }) { txn ->
            val isIncome = txn.type == "INCOME"
            val accountLabel = upiAccounts.find { it.id == txn.accountId }?.label
            val clientName = clients.find { it.id == txn.clientId }?.name

            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                // Icon
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(if (isIncome) EmeraldPrimary.copy(alpha = 0.15f) else AccentRose.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                      contentDescription = null,
                      tint = if (isIncome) EmeraldDark else AccentRose,
                      modifier = Modifier.size(20.dp)
                    )
                  }

                  Spacer(modifier = Modifier.width(12.dp))

                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = txn.vendorName ?: txn.category,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      if (txn.encryptedNote != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                          Icons.Default.Lock,
                          contentDescription = "Encrypted",
                          tint = EmeraldPrimary,
                          modifier = Modifier.size(12.dp)
                        )
                      }
                    }

                    if (txn.note.isNotBlank()) {
                      Text(
                        text = txn.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                      )
                    }

                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      modifier = Modifier.padding(top = 4.dp)
                    ) {
                      Text(
                        text = dateFormat.format(Date(txn.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                      if (txn.source != "MANUAL") {
                        StatusTag(text = txn.source, color = MaterialTheme.colorScheme.primary)
                      }
                      if (accountLabel != null) {
                        StatusTag(text = accountLabel, color = MaterialTheme.colorScheme.secondary)
                      }
                      if (clientName != null) {
                        StatusTag(text = "Client: $clientName", color = EmeraldDark)
                      }
                    }
                  }
                }

                // Amount & Delete action
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = "${if (isIncome) "+" else "-"} ${formatCurrency(txn.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isIncome) EmeraldDark else AccentRose
                  )
                  IconButton(
                    onClick = { transactionToDelete = txn },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      Icons.Default.Delete,
                      contentDescription = "Delete",
                      tint = MaterialTheme.colorScheme.outline,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Floating Action Buttons (Manual Entry & OCR Receipt)
    Column(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalAlignment = Alignment.End
    ) {
      FloatingActionButton(
        onClick = onOpenOcrScanner,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
        modifier = Modifier.size(48.dp).testTag("fab_ocr_scanner")
      ) {
        Icon(Icons.Default.DocumentScanner, contentDescription = "OCR Scan", modifier = Modifier.size(20.dp))
      }

      FloatingActionButton(
        onClick = onOpenAddTransaction,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("fab_add_transaction")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Add, contentDescription = "Add")
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Entry", fontWeight = FontWeight.Bold)
        }
      }
    }
  }

  // Confirm delete dialog
  if (transactionToDelete != null) {
    AlertDialog(
      onDismissRequest = { transactionToDelete = null },
      title = { Text("Delete Transaction") },
      text = { Text("Are you sure you want to remove this ledger entry of ${formatCurrency(transactionToDelete!!.amount)}? This action cannot be undone.") },
      confirmButton = {
        Button(
          onClick = {
            onDeleteTransaction(transactionToDelete!!)
            transactionToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { transactionToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
