package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Client
import com.example.data.model.TransactionItem
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FintechCard
import com.example.ui.components.StatusTag
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.util.ReportExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClientsScreen(
  clients: List<Client>,
  transactions: List<TransactionItem>,
  selectedClient: Client?,
  onSelectClient: (Client?) -> Unit,
  onAddClient: (name: String, phone: String, email: String, openingBal: Double, notes: String) -> Unit,
  onDeleteClient: (Client) -> Unit,
  onOpenAddTransactionForClient: (Client) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var showAddDialog by remember { mutableStateOf(false) }
  var clientToDelete by remember { mutableStateOf<Client?>(null) }
  val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

  Box(modifier = modifier.fillMaxSize().testTag("clients_screen")) {
    if (selectedClient == null) {
      // 1. All Clients Directory View
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Client Ledgers",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "B2B client accounting & audit balance sheets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        if (clients.isEmpty()) {
          item {
            EmptyStateView(
              icon = Icons.Default.Business,
              title = "No Clients Added",
              subtitle = "Tap + to add a client with contact details and opening balance."
            )
          }
        } else {
          items(clients, key = { it.id }) { client ->
            val clientTxns = transactions.filter { it.clientId == client.id }
            val totalInvoiced = clientTxns.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val totalReceived = clientTxns.filter { it.type == "INCOME" }.sumOf { it.amount }
            val netOutstanding = client.openingBalance + totalInvoiced - totalReceived

            FintechCard(
              modifier = Modifier.fillMaxWidth(),
              onClick = { onSelectClient(client) }
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      Icons.Default.Person,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(24.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column {
                    Text(
                      text = client.name,
                      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (client.phone.isNotBlank()) {
                      Text(
                        text = client.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    Text(
                      text = "${clientTxns.size} transactions on ledger",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = formatCurrency(netOutstanding),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (netOutstanding >= 0) EmeraldDark else AccentRose
                  )
                  StatusTag(
                    text = if (netOutstanding >= 0) "Receivable" else "Payable",
                    color = if (netOutstanding >= 0) EmeraldDark else AccentRose
                  )
                }
              }
            }
          }
        }
      }

      // Add Client FAB
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
          .testTag("fab_add_client")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Add, contentDescription = "Add")
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Client", fontWeight = FontWeight.Bold)
        }
      }
    } else {
      // 2. Client Detail & Audit-Ready Balance Sheet View
      val clientTxns = transactions.filter { it.clientId == selectedClient.id }
      val totalBilled = clientTxns.filter { it.type == "EXPENSE" }.sumOf { it.amount }
      val totalPaid = clientTxns.filter { it.type == "INCOME" }.sumOf { it.amount }
      val closingBalance = selectedClient.openingBalance + totalBilled - totalPaid

      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("client_detail_ledger"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Back Header
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { onSelectClient(null) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
              Spacer(modifier = Modifier.width(4.dp))
              Column {
                Text(
                  text = selectedClient.name,
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = selectedClient.phone.ifBlank { "Client Ledger" },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            IconButton(onClick = { clientToDelete = selectedClient }) {
              Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
            }
          }
        }

        // Summary Balance Sheet Card
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text("Current Balance Due", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(
                    text = formatCurrency(closingBalance),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (closingBalance >= 0) EmeraldDark else AccentRose
                  )
                }
                StatusTag(
                  text = if (closingBalance >= 0) "Client Owes" else "Advance Balance",
                  color = if (closingBalance >= 0) EmeraldDark else AccentRose
                )
              }

              Spacer(modifier = Modifier.height(14.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column {
                  Text("Opening Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(formatCurrency(selectedClient.openingBalance), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Column {
                  Text("Total Invoiced", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(formatCurrency(totalBilled), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = AccentRose)
                }
                Column {
                  Text("Total Received", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(formatCurrency(totalPaid), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = EmeraldDark)
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              // Export Audit Buttons
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    val csvContent = ReportExporter.generateClientBalanceSheetCsv(selectedClient, clientTxns)
                    ReportExporter.shareReport(context, csvContent, "BalanceSheet_${selectedClient.name.replace(" ", "_")}", "csv", "text/csv")
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).testTag("export_client_csv_button")
                ) {
                  Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("CSV Export", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                  onClick = {
                    val auditDoc = ReportExporter.formatClientAuditDocument(selectedClient, clientTxns)
                    ReportExporter.shareReport(context, auditDoc, "AuditSheet_${selectedClient.name.replace(" ", "_")}", "txt", "text/plain")
                  },
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                  modifier = Modifier.weight(1f).testTag("export_client_pdf_button")
                ) {
                  Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Audit Sheet", style = MaterialTheme.typography.labelMedium)
                }
              }
            }
          }
        }

        // Ledger History Title
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Ledger Activity (${clientTxns.size})",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            TextButton(onClick = { onOpenAddTransactionForClient(selectedClient) }) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add Entry")
            }
          }
        }

        if (clientTxns.isEmpty()) {
          item {
            EmptyStateView(
              icon = Icons.Default.Business,
              title = "No Ledger Entries",
              subtitle = "Record an invoice or payment for ${selectedClient.name} to track running balance."
            )
          }
        } else {
          var running = selectedClient.openingBalance
          items(clientTxns, key = { it.id }) { txn ->
            val isDebit = txn.type == "EXPENSE"
            val amt = txn.amount
            if (isDebit) running += amt else running -= amt

            FintechCard(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                      .background(if (isDebit) AccentRose.copy(alpha = 0.15f) else EmeraldPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = if (isDebit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                      contentDescription = null,
                      tint = if (isDebit) AccentRose else EmeraldDark,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = txn.note.ifBlank { txn.category },
                      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                      text = dateFormat.format(Date(txn.timestamp)),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = "${if (isDebit) "+" else "-"} ${formatCurrency(amt)}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isDebit) AccentRose else EmeraldDark
                  )
                  Text(
                    text = "Bal: ${formatCurrency(running)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Add Client Dialog
  if (showAddDialog) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var openingBalText by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Add Client Profile") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Client / Company Name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_client_name")
          )
          OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone / Contact") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = openingBalText,
            onValueChange = { openingBalText = it },
            label = { Text("Opening Balance (₹)") },
            placeholder = { Text("0.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_client_opening_bal")
          )
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Terms / Notes") },
            placeholder = { Text("e.g. Net 30, B2B wholesale") },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (name.isNotBlank()) {
              onAddClient(
                name,
                phone,
                email,
                openingBalText.toDoubleOrNull() ?: 0.0,
                notes
              )
              showAddDialog = false
            }
          },
          enabled = name.isNotBlank(),
          modifier = Modifier.testTag("submit_add_client_button")
        ) {
          Text("Save Client")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Delete Client Dialog
  if (clientToDelete != null) {
    AlertDialog(
      onDismissRequest = { clientToDelete = null },
      title = { Text("Delete Client") },
      text = { Text("Are you sure you want to delete ${clientToDelete!!.name}? All ledger records will be unlinked.") },
      confirmButton = {
        Button(
          onClick = {
            onDeleteClient(clientToDelete!!)
            clientToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { clientToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
