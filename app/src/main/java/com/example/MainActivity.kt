package com.example

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Client
import com.example.data.model.NotificationDraft
import com.example.ocr.ParsedReceipt
import com.example.ui.dialogs.AddTransactionDialog
import com.example.ui.dialogs.NotificationInboxDialog
import com.example.ui.screens.ClientsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.MultiDeviceSyncScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.UpiAccountsScreen
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.NavigationTab

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: AppViewModel = viewModel()
      val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()

      MyApplicationTheme(darkTheme = isDark) {
        MainAppContent(viewModel)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: AppViewModel) {
  val context = LocalContext.current
  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()

  // State flows
  val transactions by viewModel.transactions.collectAsStateWithLifecycle()
  val tasks by viewModel.tasks.collectAsStateWithLifecycle()
  val upiAccounts by viewModel.upiAccounts.collectAsStateWithLifecycle()
  val activeUpiAccount by viewModel.activeUpiAccount.collectAsStateWithLifecycle()
  val clients by viewModel.clients.collectAsStateWithLifecycle()
  val recurringBills by viewModel.recurringBills.collectAsStateWithLifecycle()
  val budgets by viewModel.budgets.collectAsStateWithLifecycle()
  val drafts by viewModel.notificationDrafts.collectAsStateWithLifecycle()
  val selectedClient by viewModel.selectedClient.collectAsStateWithLifecycle()

  // Ledger Filter States
  val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val selectedAccountFilter by viewModel.selectedAccountFilter.collectAsStateWithLifecycle()

  // OCR state
  val isOcrProcessing by viewModel.isOcrProcessing.collectAsStateWithLifecycle()
  val parsedReceipt by viewModel.parsedReceipt.collectAsStateWithLifecycle()

  // Multi-Device Range Sync States
  val isHostingSync by viewModel.isHostingServer.collectAsStateWithLifecycle()
  val serverIp by viewModel.serverIpAddress.collectAsStateWithLifecycle()
  val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
  val serverSyncCode by viewModel.serverSyncCode.collectAsStateWithLifecycle()
  val clientHostUrl by viewModel.clientHostUrl.collectAsStateWithLifecycle()
  val clientSyncCode by viewModel.clientSyncCode.collectAsStateWithLifecycle()
  val customDeviceName by viewModel.customDeviceName.collectAsStateWithLifecycle()
  val connectedClients by viewModel.connectedClients.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
  val isAutoSyncEnabled by viewModel.isAutoSyncEnabled.collectAsStateWithLifecycle()
  val lastSyncSummary by viewModel.lastSyncSummary.collectAsStateWithLifecycle()
  val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
  val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()

  // Dialog Visibility States
  var showAddTxnDialog by remember { mutableStateOf(false) }
  var showNotificationInbox by remember { mutableStateOf(false) }
  var showMultiDeviceSyncScreen by remember { mutableStateOf(false) }
  var pendingReceiptToEdit by remember { mutableStateOf<ParsedReceipt?>(null) }

  Scaffold(
    modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(EmeraldPrimary.copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.Lock,
                contentDescription = "Encrypted",
                tint = EmeraldDark,
                modifier = Modifier.size(18.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Offline Ledger",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Hardware Keystore • Offline-First",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        actions = {
          // Multi-Device Range Sync Hub Icon
          IconButton(
            onClick = { showMultiDeviceSyncScreen = true },
            modifier = Modifier.testTag("top_bar_sync_hub")
          ) {
            BadgedBox(
              badge = {
                if (isHostingSync) {
                  Badge(containerColor = EmeraldDark) { Text("HUB") }
                } else if (isAutoSyncEnabled) {
                  Badge(containerColor = AccentCyan) { Text("SYNC") }
                }
              }
            ) {
              Icon(
                Icons.Default.WifiTethering,
                contentDescription = "Multi-Device Range Sync",
                tint = if (isHostingSync) EmeraldDark else MaterialTheme.colorScheme.onSurface
              )
            }
          }

          // Dark Mode Toggle Icon
          IconButton(
            onClick = { viewModel.toggleDarkMode() },
            modifier = Modifier.testTag("top_bar_theme_toggle")
          ) {
            Icon(
              imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
              contentDescription = "Toggle Dark Mode"
            )
          }

          // Payment Confirmation Drafts Inbox Icon
          IconButton(
            onClick = { showNotificationInbox = true },
            modifier = Modifier.testTag("top_bar_notification_inbox")
          ) {
            BadgedBox(
              badge = {
                if (drafts.isNotEmpty()) {
                  Badge { Text("${drafts.size}") }
                }
              }
            ) {
              Icon(Icons.Default.Notifications, contentDescription = "Notification Drafts")
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
      ) {
        NavigationBarItem(
          selected = currentTab == NavigationTab.DASHBOARD,
          onClick = { viewModel.setTab(NavigationTab.DASHBOARD) },
          icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
          label = { Text("Home") },
          modifier = Modifier.testTag("nav_dashboard")
        )
        NavigationBarItem(
          selected = currentTab == NavigationTab.LEDGER,
          onClick = { viewModel.setTab(NavigationTab.LEDGER) },
          icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Ledger") },
          label = { Text("Ledger") },
          modifier = Modifier.testTag("nav_ledger")
        )
        NavigationBarItem(
          selected = currentTab == NavigationTab.UPI_QR,
          onClick = { viewModel.setTab(NavigationTab.UPI_QR) },
          icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "UPI QR") },
          label = { Text("UPI QR") },
          modifier = Modifier.testTag("nav_upi_qr")
        )
        NavigationBarItem(
          selected = currentTab == NavigationTab.CLIENTS,
          onClick = { viewModel.setTab(NavigationTab.CLIENTS) },
          icon = { Icon(Icons.Default.People, contentDescription = "Clients") },
          label = { Text("Clients") },
          modifier = Modifier.testTag("nav_clients")
        )
        NavigationBarItem(
          selected = currentTab == NavigationTab.TASKS,
          onClick = { viewModel.setTab(NavigationTab.TASKS) },
          icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Tasks") },
          label = { Text("Tasks") },
          modifier = Modifier.testTag("nav_tasks")
        )
        NavigationBarItem(
          selected = currentTab == NavigationTab.MORE,
          onClick = { viewModel.setTab(NavigationTab.MORE) },
          icon = { Icon(Icons.Default.Assessment, contentDescription = "Reports") },
          label = { Text("Reports") },
          modifier = Modifier.testTag("nav_reports")
        )
      }
    }
  ) { innerPadding ->
    Crossfade(
      targetState = currentTab,
      modifier = Modifier.padding(innerPadding)
    ) { tab ->
      when (tab) {
        NavigationTab.DASHBOARD -> DashboardScreen(
          transactions = transactions,
          tasks = tasks,
          recurringBills = recurringBills,
          budgets = budgets,
          notificationDrafts = drafts,
          isHostingSync = isHostingSync,
          connectedClients = connectedClients,
          isSyncing = isSyncing,
          lastSyncSummary = lastSyncSummary,
          lastSyncTime = lastSyncTime,
          clientHostUrl = clientHostUrl,
          customDeviceName = customDeviceName,
          onTriggerSync = {
            viewModel.triggerManualSync { success, msg ->
              Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
          },
          onOpenSyncScreen = { showMultiDeviceSyncScreen = true },
          onOpenReceiveUpi = { viewModel.setTab(NavigationTab.UPI_QR) },
          onOpenOcrScanner = {
            pendingReceiptToEdit = null
            showAddTxnDialog = true
          },
          onOpenAddTransaction = {
            pendingReceiptToEdit = null
            showAddTxnDialog = true
          },
          onOpenNotificationInbox = { showNotificationInbox = true },
          onPayBill = { bill ->
            viewModel.payRecurringBill(bill, activeUpiAccount?.id)
            Toast.makeText(context, "Settled bill: ${bill.title}", Toast.LENGTH_SHORT).show()
          },
          onToggleTaskDone = { task -> viewModel.toggleTaskDone(task) },
          onViewAllTasks = { viewModel.setTab(NavigationTab.TASKS) },
          onViewAllReports = { viewModel.setTab(NavigationTab.MORE) }
        )

        NavigationTab.LEDGER -> LedgerScreen(
          transactions = transactions,
          upiAccounts = upiAccounts,
          clients = clients,
          draftsCount = drafts.size,
          typeFilter = typeFilter,
          searchQuery = searchQuery,
          selectedAccountId = selectedAccountFilter,
          onTypeFilterChange = { viewModel.setTypeFilter(it) },
          onSearchChange = { viewModel.setSearchQuery(it) },
          onAccountFilterChange = { viewModel.setAccountFilter(it) },
          onOpenAddTransaction = {
            pendingReceiptToEdit = null
            showAddTxnDialog = true
          },
          onOpenOcrScanner = {
            pendingReceiptToEdit = null
            showAddTxnDialog = true
          },
          onOpenNotificationInbox = { showNotificationInbox = true },
          onDeleteTransaction = { viewModel.deleteTransaction(it) }
        )

        NavigationTab.UPI_QR -> UpiAccountsScreen(
          accounts = upiAccounts,
          activeAccount = activeUpiAccount,
          transactions = transactions,
          onSwitchAccount = { viewModel.switchActiveUpiAccount(it) },
          onAddAccount = { upiId, payee, label, bank, mLimit, qLimit, isEnforced ->
            viewModel.addUpiAccount(upiId, payee, label, bank, mLimit, qLimit, isEnforced)
          },
          onUpdateAccountLimits = { account, mLimit, qLimit, isEnforced ->
            viewModel.updateUpiAccountLimits(account, mLimit, qLimit, isEnforced)
          },
          onDeleteAccount = { viewModel.deleteUpiAccount(it) },
          onOpenMultiDeviceSync = { showMultiDeviceSyncScreen = true }
        )

        NavigationTab.CLIENTS -> ClientsScreen(
          clients = clients,
          transactions = transactions,
          selectedClient = selectedClient,
          onSelectClient = { viewModel.selectClient(it) },
          onAddClient = { name, phone, email, openBal, notes ->
            viewModel.addClient(name, phone, email, openBal, notes)
          },
          onDeleteClient = { viewModel.deleteClient(it) },
          onOpenAddTransactionForClient = { client ->
            viewModel.selectClient(client)
            pendingReceiptToEdit = null
            showAddTxnDialog = true
          }
        )

        NavigationTab.TASKS -> TasksScreen(
          tasks = tasks,
          onAddTask = { viewModel.addTask(it) },
          onUpdateTask = { viewModel.updateTask(it) },
          onToggleTaskDone = { viewModel.toggleTaskDone(it) },
          onUpdateProgress = { task, progress -> viewModel.updateTaskProgress(task, progress) },
          onDeleteTask = { viewModel.deleteTask(it) }
        )

        NavigationTab.MORE -> ReportsScreen(
          transactions = transactions,
          recurringBills = recurringBills,
          budgets = budgets,
          upiAccounts = upiAccounts,
          isDarkMode = isDark,
          onToggleDarkMode = { viewModel.toggleDarkMode() },
          onPayBill = { bill, accId ->
            viewModel.payRecurringBill(bill, accId)
            Toast.makeText(context, "Paid: ${bill.title}", Toast.LENGTH_SHORT).show()
          },
          onAddBill = { title, amt, day, cat, upiId ->
            viewModel.addRecurringBill(title, amt, day, cat, upiId)
          },
          onDeleteBill = { viewModel.deleteRecurringBill(it) },
          onSetBudget = { cat, limit -> viewModel.setBudgetGoal(cat, limit) },
          onDeleteBudget = { viewModel.deleteBudgetGoal(it) }
        )
      }
    }
  }

  // Add / Edit Transaction Dialog with integrated OCR
  if (showAddTxnDialog) {
    AddTransactionDialog(
      upiAccounts = upiAccounts,
      clients = clients,
      transactions = transactions,
      prefilledReceipt = pendingReceiptToEdit,
      isOcrLoading = isOcrProcessing,
      onScanReceiptRequest = { bitmap ->
        viewModel.processReceiptImage(bitmap) { parsed ->
          pendingReceiptToEdit = parsed
        }
      },
      onDismiss = {
        showAddTxnDialog = false
        pendingReceiptToEdit = null
      },
      onSave = { amount, type, category, accId, clientId, note, vendor, source ->
        viewModel.addTransaction(
          amount = amount,
          type = type,
          category = category,
          accountId = accId,
          clientId = clientId,
          note = note,
          vendorName = vendor,
          source = source
        )
        Toast.makeText(context, "Logged transaction to ledger", Toast.LENGTH_SHORT).show()
        showAddTxnDialog = false
        pendingReceiptToEdit = null
      }
    )
  }

  // Payment Confirmation Notification Inbox
  if (showNotificationInbox) {
    NotificationInboxDialog(
      drafts = drafts,
      onConfirmDraft = { draft ->
        val active = activeUpiAccount
        if (draft.type == "INCOME" && active != null) {
          val limitCheck = viewModel.checkUpiLimit(active.id, draft.amount)
          if (limitCheck is com.example.util.LimitValidationResult.Blocked && active.isLimitEnforced) {
            Toast.makeText(
              context,
              "Restricted: Payment exceeds ${limitCheck.period} receiving limit for ${active.label} (Remaining: ₹${limitCheck.remaining})",
              Toast.LENGTH_LONG
            ).show()
            return@NotificationInboxDialog
          }
        }
        viewModel.addTransaction(
          amount = draft.amount,
          type = draft.type,
          category = "UPI Transfer",
          accountId = active?.id,
          clientId = null,
          note = "${draft.senderApp}: ${draft.rawText}",
          vendorName = draft.senderOrReceiver,
          source = "NOTIFICATION"
        )
        viewModel.dismissNotificationDraft(draft.id)
        Toast.makeText(context, "Posted into ledger!", Toast.LENGTH_SHORT).show()
      },
      onDismissDraft = { draftId ->
        viewModel.dismissNotificationDraft(draftId)
      },
      onAddTestNotification = {
        val testDraft = NotificationDraft(
          id = "sim_${System.currentTimeMillis()}",
          senderApp = "Paytm",
          amount = (100..5000).random().toDouble(),
          type = if (listOf(true, false).random()) "INCOME" else "EXPENSE",
          rawText = "Received ₹${(100..5000).random()} from Apex Tech via UPI",
          senderOrReceiver = "Apex Tech",
          timestamp = System.currentTimeMillis(),
          upiReference = "UPI/908234123"
        )
        viewModel.addTestNotificationDraft(testDraft)
        Toast.makeText(context, "Simulated payment notification received!", Toast.LENGTH_SHORT).show()
      },
      onClose = { showNotificationInbox = false }
    )
  }

  // Multi-Device Range Sync Overlay
  if (showMultiDeviceSyncScreen) {
    MultiDeviceSyncScreen(
      isHosting = isHostingSync,
      serverIp = serverIp,
      serverPort = serverPort,
      serverSyncCode = serverSyncCode,
      clientHostUrl = clientHostUrl,
      clientSyncCode = clientSyncCode,
      customDeviceName = customDeviceName,
      connectedClients = connectedClients,
      isSyncing = isSyncing,
      isAutoSyncEnabled = isAutoSyncEnabled,
      lastSyncSummary = lastSyncSummary,
      lastSyncTime = lastSyncTime,
      syncLogs = syncLogs,
      onStartServer = { viewModel.startHostSyncServer() },
      onStopServer = { viewModel.stopHostSyncServer() },
      onRegenerateCode = { viewModel.generateNewServerSyncCode() },
      onUpdateClientUrl = { viewModel.setClientHostUrl(it) },
      onUpdateClientCode = { viewModel.setClientSyncCode(it) },
      onUpdateCustomDeviceName = { viewModel.setCustomDeviceName(it) },
      onSyncNow = { url, code, cb -> viewModel.syncWithHost(url, code, cb) },
      onTestPing = { url, cb -> viewModel.testHostConnection(url, cb) },
      onToggleAutoSync = { viewModel.toggleAutoSync(it) },
      onSimulateReceivedPayment = { amt, payer ->
        val activeAcc = upiAccounts.firstOrNull { it.isActive } ?: upiAccounts.firstOrNull()
        viewModel.addTransaction(
          amount = amt,
          type = "INCOME",
          category = "UPI Transfer",
          accountId = activeAcc?.id,
          clientId = null,
          note = "Customer payment via UPI",
          vendorName = payer,
          source = customDeviceName
        )
      },
      onBack = { showMultiDeviceSyncScreen = false }
    )
  }
}
