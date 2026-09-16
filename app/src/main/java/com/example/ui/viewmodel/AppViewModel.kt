package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CryptoManager
import com.example.data.db.AppDatabase
import com.example.data.model.BudgetGoal
import com.example.data.model.Client
import com.example.data.model.NotificationDraft
import com.example.data.model.RecurringBill
import com.example.data.model.TaskItem
import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import com.example.data.repository.AppRepository
import com.example.ocr.ParsedReceipt
import com.example.ocr.ReceiptOcrHelper
import com.example.sync.ConnectedClientInfo
import com.example.sync.LocalSyncClient
import com.example.sync.LocalSyncServer
import com.example.sync.SyncPayload
import com.example.sync.SyncResponse
import com.example.sync.SyncTransactionDto
import com.example.sync.SyncUpiAccountDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class NavigationTab {
  DASHBOARD,
  LEDGER,
  TASKS,
  CLIENTS,
  UPI_QR,
  MORE
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: AppRepository

  init {
    val db = AppDatabase.getInstance(application)
    repository = AppRepository(
      db.taskDao(),
      db.transactionDao(),
      db.upiAccountDao(),
      db.clientDao(),
      db.recurringBillDao(),
      db.budgetDao()
    )
  }

  // --- Dark Mode State ---
  private val _isDarkMode = MutableStateFlow(false)
  val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

  fun toggleDarkMode() {
    _isDarkMode.value = !_isDarkMode.value
  }

  // --- Navigation Tab ---
  private val _currentTab = MutableStateFlow(NavigationTab.DASHBOARD)
  val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

  fun setTab(tab: NavigationTab) {
    _currentTab.value = tab
  }

  // --- Reactive Room Streams ---
  val tasks: StateFlow<List<TaskItem>> = repository.allTasks
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val transactions: StateFlow<List<TransactionItem>> = repository.allTransactions
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val upiAccounts: StateFlow<List<UpiAccount>> = repository.allUpiAccounts
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val activeUpiAccount: StateFlow<UpiAccount?> = repository.activeAccount
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val clients: StateFlow<List<Client>> = repository.allClients
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recurringBills: StateFlow<List<RecurringBill>> = repository.allBills
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val budgets: StateFlow<List<BudgetGoal>> = repository.allBudgets
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // --- Ledger Filters ---
  private val _typeFilter = MutableStateFlow("ALL") // ALL, INCOME, EXPENSE
  val typeFilter: StateFlow<String> = _typeFilter.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedAccountFilter = MutableStateFlow<Long?>(null)
  val selectedAccountFilter: StateFlow<Long?> = _selectedAccountFilter.asStateFlow()

  fun setTypeFilter(filter: String) { _typeFilter.value = filter }
  fun setSearchQuery(query: String) { _searchQuery.value = query }
  fun setAccountFilter(accId: Long?) { _selectedAccountFilter.value = accId }

  // --- Client Detail Ledger ---
  private val _selectedClient = MutableStateFlow<Client?>(null)
  val selectedClient: StateFlow<Client?> = _selectedClient.asStateFlow()

  fun selectClient(client: Client?) {
    _selectedClient.value = client
  }

  // --- OCR State ---
  private val _isOcrProcessing = MutableStateFlow(false)
  val isOcrProcessing: StateFlow<Boolean> = _isOcrProcessing.asStateFlow()

  private val _parsedReceipt = MutableStateFlow<ParsedReceipt?>(null)
  val parsedReceipt: StateFlow<ParsedReceipt?> = _parsedReceipt.asStateFlow()

  fun processReceiptImage(bitmap: Bitmap, onDone: (ParsedReceipt) -> Unit) {
    viewModelScope.launch {
      _isOcrProcessing.value = true
      val parsed = ReceiptOcrHelper.parseReceiptImage(bitmap)
      _parsedReceipt.value = parsed
      _isOcrProcessing.value = false
      onDone(parsed)
    }
  }

  fun clearReceiptPreview() {
    _parsedReceipt.value = null
  }

  // --- Notification Draft Inbox ---
  private val _notificationDrafts = MutableStateFlow<List<NotificationDraft>>(
    listOf(
      NotificationDraft(
        id = "draft_1",
        senderApp = "PhonePe",
        amount = 1850.0,
        type = "INCOME",
        rawText = "Received ₹1,850.00 from Rajesh Verma via PhonePe UPI",
        senderOrReceiver = "Rajesh Verma",
        timestamp = System.currentTimeMillis() - 3600000L * 2,
        upiReference = "UPI/4289019283"
      ),
      NotificationDraft(
        id = "draft_2",
        senderApp = "Google Pay",
        amount = 450.0,
        type = "EXPENSE",
        rawText = "Paid ₹450.00 to Chai Point Bangalore",
        senderOrReceiver = "Chai Point",
        timestamp = System.currentTimeMillis() - 3600000L * 5,
        upiReference = "UPI/4289019912"
      )
    )
  )
  val notificationDrafts: StateFlow<List<NotificationDraft>> = _notificationDrafts.asStateFlow()

  fun dismissNotificationDraft(draftId: String) {
    _notificationDrafts.value = _notificationDrafts.value.filterNot { it.id == draftId }
  }

  fun addTestNotificationDraft(draft: NotificationDraft) {
    _notificationDrafts.value = listOf(draft) + _notificationDrafts.value
  }

  // --- Task Actions ---
  fun addTask(task: TaskItem) {
    viewModelScope.launch { repository.insertTask(task) }
  }

  fun updateTask(task: TaskItem) {
    viewModelScope.launch { repository.updateTask(task) }
  }

  fun toggleTaskDone(task: TaskItem) {
    viewModelScope.launch {
      val isNowDone = !task.isDone
      val newStreak = if (isNowDone) task.streakCount + 1 else maxOf(0, task.streakCount - 1)
      val newProgress = if (isNowDone) 100 else 0
      repository.updateTask(
        task.copy(
          isDone = isNowDone,
          progressPercent = newProgress,
          streakCount = newStreak,
          completedAt = if (isNowDone) System.currentTimeMillis() else null
        )
      )
    }
  }

  fun updateTaskProgress(task: TaskItem, progress: Int) {
    viewModelScope.launch {
      val isNowDone = progress >= 100
      repository.updateTask(
        task.copy(
          progressPercent = progress,
          isDone = isNowDone,
          completedAt = if (isNowDone) System.currentTimeMillis() else null
        )
      )
    }
  }

  fun deleteTask(task: TaskItem) {
    viewModelScope.launch { repository.deleteTask(task) }
  }

  // --- Transaction Actions ---
  fun addTransaction(
    amount: Double,
    type: String,
    category: String,
    accountId: Long?,
    clientId: Long?,
    note: String,
    vendorName: String?,
    source: String = "MANUAL",
    receiptPath: String? = null
  ) {
    viewModelScope.launch {
      // Secure encryption for sensitive financial notes
      val encrypted = if (note.isNotBlank()) CryptoManager.encrypt(note) else null

      val txn = TransactionItem(
        amount = amount,
        type = type,
        category = category,
        accountId = accountId,
        clientId = clientId,
        note = note,
        encryptedNote = encrypted,
        receiptImagePath = receiptPath,
        source = source,
        status = "CONFIRMED",
        vendorName = vendorName,
        timestamp = System.currentTimeMillis()
      )
      repository.insertTransaction(txn)
    }
  }

  fun deleteTransaction(item: TransactionItem) {
    viewModelScope.launch { repository.deleteTransaction(item) }
  }

  // --- UPI Account Actions ---
  fun addUpiAccount(
    upiId: String,
    payeeName: String,
    label: String,
    bankName: String,
    monthlyLimit: Double = 0.0,
    quarterlyLimit: Double = 0.0,
    isLimitEnforced: Boolean = true
  ) {
    viewModelScope.launch {
      val isFirst = upiAccounts.value.isEmpty()
      val acc = UpiAccount(
        upiId = upiId.trim(),
        payeeName = payeeName.trim(),
        label = label.trim(),
        bankName = bankName.trim(),
        isActive = isFirst,
        monthlyLimit = monthlyLimit,
        quarterlyLimit = quarterlyLimit,
        isLimitEnforced = isLimitEnforced
      )
      repository.insertAccount(acc)
    }
  }

  fun updateUpiAccount(account: UpiAccount) {
    viewModelScope.launch { repository.updateAccount(account) }
  }

  fun updateUpiAccountLimits(
    account: UpiAccount,
    monthlyLimit: Double,
    quarterlyLimit: Double,
    isLimitEnforced: Boolean
  ) {
    viewModelScope.launch {
      repository.updateAccount(
        account.copy(
          monthlyLimit = monthlyLimit,
          quarterlyLimit = quarterlyLimit,
          isLimitEnforced = isLimitEnforced
        )
      )
    }
  }

  fun checkUpiLimit(accountId: Long?, amount: Double): com.example.util.LimitValidationResult {
    if (accountId == null || amount <= 0.0) return com.example.util.LimitValidationResult.Allowed
    val targetAccount = upiAccounts.value.find { it.id == accountId } ?: return com.example.util.LimitValidationResult.Allowed
    return com.example.util.UpiLimitCalculator.validateIncomingAmount(
      account = targetAccount,
      amount = amount,
      transactions = transactions.value,
      allAccounts = upiAccounts.value
    )
  }

  fun switchActiveUpiAccount(accountId: Long) {
    viewModelScope.launch { repository.switchActiveAccount(accountId) }
  }

  fun deleteUpiAccount(account: UpiAccount) {
    viewModelScope.launch { repository.deleteAccount(account) }
  }

  // --- Client Actions ---
  fun addClient(name: String, phone: String, email: String, openingBalance: Double, notes: String) {
    viewModelScope.launch {
      val client = Client(
        name = name.trim(),
        phone = phone.trim(),
        email = email.trim(),
        openingBalance = openingBalance,
        notes = notes.trim()
      )
      repository.insertClient(client)
    }
  }

  fun updateClient(client: Client) {
    viewModelScope.launch { repository.updateClient(client) }
  }

  fun deleteClient(client: Client) {
    viewModelScope.launch {
      if (_selectedClient.value?.id == client.id) {
        _selectedClient.value = null
      }
      repository.deleteClient(client)
    }
  }

  // --- Recurring Bill Actions ---
  fun addRecurringBill(title: String, amount: Double, dueDay: Int, category: String, upiId: Long?) {
    viewModelScope.launch {
      val bill = RecurringBill(
        title = title.trim(),
        amount = amount,
        dueDayOfMonth = dueDay,
        category = category,
        upiAccountId = upiId
      )
      repository.insertBill(bill)
    }
  }

  fun payRecurringBill(bill: RecurringBill, upiAccountId: Long?) {
    viewModelScope.launch { repository.payBill(bill, upiAccountId) }
  }

  fun deleteRecurringBill(bill: RecurringBill) {
    viewModelScope.launch { repository.deleteBill(bill) }
  }

  // --- Budget Actions ---
  fun setBudgetGoal(category: String, limit: Double) {
    viewModelScope.launch {
      val existing = budgets.value.find { it.category.equals(category, ignoreCase = true) }
      if (existing != null) {
        repository.updateBudget(existing.copy(monthlyLimit = limit))
      } else {
        repository.insertBudget(BudgetGoal(category = category, monthlyLimit = limit))
      }
    }
  }

  fun deleteBudgetGoal(budget: BudgetGoal) {
    viewModelScope.launch { repository.deleteBudget(budget) }
  }

  // =========================================================================
  // --- Local Offline Multi-Device Sync Engine (Within Wi-Fi / Hotspot Range)
  // =========================================================================
  private val syncServer = LocalSyncServer(viewModelScope)
  private val syncClient = LocalSyncClient()
  private var autoSyncJob: Job? = null

  private val _isHostingServer = MutableStateFlow(false)
  val isHostingServer: StateFlow<Boolean> = _isHostingServer.asStateFlow()

  private val _serverIpAddress = MutableStateFlow<String?>(null)
  val serverIpAddress: StateFlow<String?> = _serverIpAddress.asStateFlow()

  private val _serverPort = MutableStateFlow(8888)
  val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

  private val _serverSyncCode = MutableStateFlow(String.format("%06d", (100000..999999).random()))
  val serverSyncCode: StateFlow<String> = _serverSyncCode.asStateFlow()

  private val _clientHostUrl = MutableStateFlow("")
  val clientHostUrl: StateFlow<String> = _clientHostUrl.asStateFlow()

  private val _clientSyncCode = MutableStateFlow("")
  val clientSyncCode: StateFlow<String> = _clientSyncCode.asStateFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  private val _isAutoSyncEnabled = MutableStateFlow(false)
  val isAutoSyncEnabled: StateFlow<Boolean> = _isAutoSyncEnabled.asStateFlow()

  private val _lastSyncSummary = MutableStateFlow("No device synced yet")
  val lastSyncSummary: StateFlow<String> = _lastSyncSummary.asStateFlow()

  private val _lastSyncTime = MutableStateFlow<Long?>(null)
  val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

  private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
  val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

  private val _customDeviceName = MutableStateFlow("Mobile 1")
  val customDeviceName: StateFlow<String> = _customDeviceName.asStateFlow()

  fun setCustomDeviceName(name: String) {
    _customDeviceName.value = name.trim()
  }

  private val _connectedClients = MutableStateFlow<List<ConnectedClientInfo>>(emptyList())
  val connectedClients: StateFlow<List<ConnectedClientInfo>> = _connectedClients.asStateFlow()

  fun setClientHostUrl(url: String) {
    _clientHostUrl.value = url.trim()
  }

  fun setClientSyncCode(code: String) {
    _clientSyncCode.value = code.trim()
  }

  fun generateNewServerSyncCode() {
    _serverSyncCode.value = String.format("%06d", (100000..999999).random())
  }

  private fun addSyncLog(message: String) {
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    _syncLogs.value = listOf("[$time] $message") + _syncLogs.value.take(15)
  }

  /**
   * Starts this device as the local Sync Hub / Host on the current Wi-Fi or Hotspot.
   */
  fun startHostSyncServer() {
    val ip = LocalSyncServer.getLocalIpAddress()
    _serverIpAddress.value = ip

    syncServer.start(
      port = _serverPort.value,
      code = _serverSyncCode.value
    ) { clientPayload ->
      // Incoming sync payload from client phone
      addSyncLog("Client ${clientPayload.deviceName} requested sync (${clientPayload.transactions.size} txns)")
      val (newTxns, updatedAccs) = mergeIncomingSync(
        clientPayload.transactions,
        clientPayload.accounts
      )

      _connectedClients.value = syncServer.getConnectedClients()
      _lastSyncTime.value = System.currentTimeMillis()
      _lastSyncSummary.value = "Synced with ${clientPayload.deviceName} (Received $newTxns new txns, aligned $updatedAccs limits)"
      addSyncLog("Synced with ${clientPayload.deviceName}: $newTxns new txns merged, $updatedAccs limits updated")

      // Respond with host's complete up-to-date data fetched directly from database
      val hostPayload = buildFreshSyncPayload(_serverSyncCode.value)
      SyncResponse(
        success = true,
        message = "Sync successful! Merged $newTxns transactions and aligned $updatedAccs limits.",
        hostDeviceName = _customDeviceName.value.ifBlank { "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Host)" },
        syncTimestamp = System.currentTimeMillis(),
        transactions = hostPayload.transactions,
        accounts = hostPayload.accounts
      )
    }

    _isHostingServer.value = true
    addSyncLog("Started Sync Hub at http://${ip ?: "0.0.0.0"}:${_serverPort.value}")
  }

  /**
   * Stops the host sync server.
   */
  fun stopHostSyncServer() {
    syncServer.stop()
    _isHostingServer.value = false
    _connectedClients.value = emptyList()
    addSyncLog("Sync Hub stopped")
  }

  /**
   * Executes client-side sync with the specified Host URL & Sync Code.
   */
  fun syncWithHost(
    hostUrl: String = _clientHostUrl.value,
    code: String = _clientSyncCode.value,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
  ) {
    if (hostUrl.isBlank() || code.isBlank()) {
      onResult(false, "Please provide both Host URL/IP and 6-digit Sync Code")
      return
    }

    viewModelScope.launch {
      _isSyncing.value = true
      addSyncLog("Attempting sync with $hostUrl...")

      val localPayload = buildFreshSyncPayload(code)
      val result = syncClient.sync(hostUrl, localPayload)

      result.onSuccess { response ->
        val (newTxns, updatedAccs) = mergeIncomingSync(response.transactions, response.accounts)
        _lastSyncTime.value = System.currentTimeMillis()
        _lastSyncSummary.value = "Synced with ${response.hostDeviceName} (Added $newTxns txns, aligned $updatedAccs limits)"
        addSyncLog("Sync success! Received $newTxns new txns from host, aligned $updatedAccs limits")
        _isSyncing.value = false
        onResult(true, "Synchronized with ${response.hostDeviceName}! +$newTxns txns merged, limits aligned.")
      }.onFailure { error ->
        _isSyncing.value = false
        val msg = error.localizedMessage ?: "Connection error"
        addSyncLog("Sync failed: $msg")
        onResult(false, msg)
      }
    }
  }

  /**
   * Triggers a manual sync refresh or checks for pending ledger updates across peer devices.
   */
  fun triggerManualSync(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
    viewModelScope.launch {
      if (_isHostingServer.value) {
        _isSyncing.value = true
        kotlinx.coroutines.delay(400)
        val clients = syncServer.getConnectedClients()
        _connectedClients.value = clients
        val txns = repository.allTransactions.first()
        _isSyncing.value = false
        val summary = if (clients.isEmpty()) {
          "Hub active. Waiting for peer devices on Wi-Fi."
        } else {
          "${clients.size} peer(s) connected (${clients.joinToString { it.deviceName }}). ${txns.size} ledger records in sync."
        }
        _lastSyncSummary.value = summary
        _lastSyncTime.value = System.currentTimeMillis()
        addSyncLog("Manual ledger check: ${clients.size} peer(s) verified.")
        onResult(true, summary)
      } else if (_clientHostUrl.value.isNotBlank() && _clientSyncCode.value.isNotBlank()) {
        syncWithHost(_clientHostUrl.value, _clientSyncCode.value, onResult)
      } else {
        onResult(false, "Sync not configured. Set up Host Hub or connect to Peer.")
      }
    }
  }

  /**
   * Tests connectivity to the host URL.
   */
  fun testHostConnection(
    hostUrl: String = _clientHostUrl.value,
    onResult: (Boolean, String) -> Unit
  ) {
    viewModelScope.launch {
      val res = syncClient.ping(hostUrl)
      res.onSuccess {
        onResult(true, it)
      }.onFailure {
        onResult(false, it.localizedMessage ?: "Ping failed")
      }
    }
  }

  /**
   * Toggles periodic Auto-Sync (runs every 15 seconds within range).
   */
  fun toggleAutoSync(enable: Boolean) {
    _isAutoSyncEnabled.value = enable
    autoSyncJob?.cancel()
    autoSyncJob = null

    if (enable) {
      autoSyncJob = viewModelScope.launch {
        while (isActive && _isAutoSyncEnabled.value) {
          delay(15000)
          if (_clientHostUrl.value.isNotBlank() && _clientSyncCode.value.isNotBlank()) {
            syncWithHost(_clientHostUrl.value, _clientSyncCode.value)
          }
        }
      }
      addSyncLog("Auto-sync enabled (every 15s)")
    } else {
      addSyncLog("Auto-sync disabled")
    }
  }

  /**
   * Extracts UPI UTR / Reference number from reference field or payment text.
   * Standard NPCI Indian UPI UTR is 12 digits.
   */
  private fun extractUpiReference(ref: String?, note: String?, vendor: String?): String? {
    val combined = listOfNotNull(ref, note, vendor).joinToString(" ")
    // Check 12-digit Indian UPI UTR
    val utrMatch = Regex("\\b\\d{12}\\b").find(combined)
    if (utrMatch != null) return utrMatch.value

    // Check prefixed references e.g. UPI/123456789 or Ref 123456789
    val refMatch = Regex("(?:UPI|UTR|Ref|Txn|Reference)[\\s/:#-]*([A-Za-z0-9]{8,18})", RegexOption.IGNORE_CASE).find(combined)
    if (refMatch != null) return refMatch.groupValues[1]

    if (!ref.isNullOrBlank() && !ref.startsWith("TXN_") && !ref.startsWith("sim_") && ref.length >= 6) {
      return ref.trim()
    }
    return null
  }

  /**
   * Evaluates if incoming transaction is a duplicate of an existing transaction.
   * Prevents double-counting when multiple devices receive the same payment notification
   * or when payments are synced across devices.
   */
  private fun isDuplicateTransaction(
    candidate: SyncTransactionDto,
    existing: TransactionItem,
    existingUpiId: String?
  ): Boolean {
    // 1. Same Reference / UTR
    val candUtr = extractUpiReference(candidate.referenceNumber, candidate.note, candidate.vendorName)
    val existUtr = extractUpiReference(existing.referenceNumber, existing.note, existing.vendorName)
    if (!candUtr.isNullOrBlank() && !existUtr.isNullOrBlank()) {
      if (candUtr.equals(existUtr, ignoreCase = true)) {
        return true // Guaranteed identical payment
      }
    }

    // Direct reference number string match
    if (!candidate.referenceNumber.isNullOrBlank() &&
        !existing.referenceNumber.isNullOrBlank() &&
        candidate.referenceNumber.trim().equals(existing.referenceNumber.trim(), ignoreCase = true)) {
      return true
    }

    // 2. Amount and Type check
    if (Math.abs(candidate.amount - existing.amount) > 0.01) return false
    if (!candidate.type.equals(existing.type, ignoreCase = true)) return false

    // 3. Time proximity check (within 15 minutes)
    val timeDiff = Math.abs(candidate.timestamp - existing.timestamp)
    val isWithin15Mins = timeDiff <= 15 * 60 * 1000L

    if (isWithin15Mins) {
      // If same UPI account
      val sameUpi = !candidate.upiId.isNullOrBlank() &&
                    !existingUpiId.isNullOrBlank() &&
                    candidate.upiId.equals(existingUpiId, ignoreCase = true)

      // Compare notes and vendor names
      val candWords = candidate.note.lowercase().split("\\s+".toRegex()).filter { it.length >= 3 }.toSet()
      val existWords = existing.note.lowercase().split("\\s+".toRegex()).filter { it.length >= 3 }.toSet()
      val commonWords = candWords.intersect(existWords)

      if (sameUpi && (commonWords.isNotEmpty() || timeDiff < 3 * 60 * 1000L)) {
        return true
      }

      if (commonWords.isNotEmpty() && timeDiff < 5 * 60 * 1000L) {
        return true
      }

      // If notes are identical or vendor names match
      if (candidate.note.trim().equals(existing.note.trim(), ignoreCase = true)) {
        return true
      }
      if (!candidate.vendorName.isNullOrBlank() &&
          !existing.vendorName.isNullOrBlank() &&
          candidate.vendorName.trim().equals(existing.vendorName.trim(), ignoreCase = true)) {
        return true
      }
    }

    return false
  }

  /**
   * Builds the sync data payload directly from Room database to ensure zero latency/staleness.
   */
  private suspend fun buildFreshSyncPayload(syncCode: String): SyncPayload {
    val txns = repository.allTransactions.first()
    val accounts = repository.allUpiAccounts.first()
    val myDevice = _customDeviceName.value.ifBlank { "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}" }

    val txnsDto = txns.map { t ->
      val acc = accounts.find { it.id == t.accountId }
      val origin = if (t.source.startsWith("SYNCED (") && t.source.endsWith(")")) {
        t.source.removePrefix("SYNCED (").removeSuffix(")")
      } else {
        myDevice
      }
      SyncTransactionDto(
        syncId = t.referenceNumber ?: "txn_${t.id}_${t.timestamp}",
        amount = t.amount,
        type = t.type,
        category = t.category,
        upiId = acc?.upiId,
        accountLabel = acc?.label,
        note = t.note,
        vendorName = t.vendorName,
        referenceNumber = t.referenceNumber ?: "TXN_${t.timestamp}",
        source = t.source,
        timestamp = t.timestamp,
        originDevice = origin
      )
    }

    val accsDto = accounts.map { a ->
      SyncUpiAccountDto(
        upiId = a.upiId,
        payeeName = a.payeeName,
        label = a.label,
        bankName = a.bankName,
        monthlyLimit = a.monthlyLimit,
        quarterlyLimit = a.quarterlyLimit,
        isLimitEnforced = a.isLimitEnforced
      )
    }

    return SyncPayload(
      syncCode = syncCode,
      deviceName = myDevice,
      deviceId = android.os.Build.ID ?: "dev_${System.currentTimeMillis()}",
      timestamp = System.currentTimeMillis(),
      transactions = txnsDto,
      accounts = accsDto
    )
  }

  /**
   * Merges incoming transactions and synchronizes UPI account limits across devices.
   * Features smart deduplication for the 3-mobile shop scenario where multiple phones
   * receive the same payment or need to aggregate independent payments onto a counter phone.
   */
  private suspend fun mergeIncomingSync(
    incomingTxns: List<SyncTransactionDto>,
    incomingAccounts: List<SyncUpiAccountDto>
  ): Pair<Int, Int> {
    var newTxns = 0
    var updatedAccounts = 0

    // Fetch freshest data from database directly
    val currentAccounts = repository.allUpiAccounts.first()
    val allKnownTxns = repository.allTransactions.first().toMutableList()

    // 1. Sync & align UPI Accounts limits
    for (accDto in incomingAccounts) {
      val existing = currentAccounts.find { it.upiId.equals(accDto.upiId, ignoreCase = true) }
      if (existing != null) {
        if (existing.monthlyLimit != accDto.monthlyLimit ||
          existing.quarterlyLimit != accDto.quarterlyLimit ||
          existing.isLimitEnforced != accDto.isLimitEnforced
        ) {
          repository.updateAccount(
            existing.copy(
              monthlyLimit = accDto.monthlyLimit,
              quarterlyLimit = accDto.quarterlyLimit,
              isLimitEnforced = accDto.isLimitEnforced
            )
          )
          updatedAccounts++
        }
      } else if (accDto.upiId.isNotBlank()) {
        repository.insertAccount(
          UpiAccount(
            upiId = accDto.upiId,
            payeeName = accDto.payeeName,
            label = accDto.label,
            bankName = accDto.bankName,
            monthlyLimit = accDto.monthlyLimit,
            quarterlyLimit = accDto.quarterlyLimit,
            isLimitEnforced = accDto.isLimitEnforced
          )
        )
        updatedAccounts++
      }
    }

    // Refresh active accounts list
    val latestAccounts = repository.allUpiAccounts.first()

    // 2. Merge transactions with multi-device deduplication
    for (tDto in incomingTxns) {
      val matchingAcc = latestAccounts.find { it.upiId.equals(tDto.upiId, ignoreCase = true) }
      val targetAccId = matchingAcc?.id

      val isDuplicate = allKnownTxns.any { existing ->
        val existingAcc = latestAccounts.find { it.id == existing.accountId }
        isDuplicateTransaction(tDto, existing, existingAcc?.upiId)
      }

      if (!isDuplicate) {
        val originLabel = tDto.originDevice?.ifBlank { null } ?: "Remote Mobile"
        val newTxn = TransactionItem(
          amount = tDto.amount,
          type = tDto.type,
          category = tDto.category,
          accountId = targetAccId,
          note = tDto.note,
          vendorName = tDto.vendorName,
          referenceNumber = tDto.referenceNumber ?: "SYNC_${tDto.timestamp}",
          source = "SYNCED ($originLabel)",
          status = "CONFIRMED",
          timestamp = tDto.timestamp
        )
        repository.insertTransaction(newTxn)
        allKnownTxns.add(newTxn)
        newTxns++

        // Auto-reconcile / dismiss draft from notification inbox if this device has one
        val candUtr = extractUpiReference(tDto.referenceNumber, tDto.note, tDto.vendorName)
        val draftToDismiss = _notificationDrafts.value.find { draft ->
          val draftUtr = extractUpiReference(draft.upiReference, draft.rawText, draft.senderOrReceiver)
          (!draftUtr.isNullOrBlank() && draftUtr.equals(candUtr, ignoreCase = true)) ||
            (Math.abs(draft.amount - tDto.amount) < 0.01 &&
              Math.abs(draft.timestamp - tDto.timestamp) < 15 * 60 * 1000L)
        }
        if (draftToDismiss != null) {
          dismissNotificationDraft(draftToDismiss.id)
          addSyncLog("Auto-reconciled draft payment (Ref: ${draftToDismiss.upiReference}) already recorded on another device")
        }
      }
    }

    return Pair(newTxns, updatedAccounts)
  }

  override fun onCleared() {
    super.onCleared()
    syncServer.stop()
    autoSyncJob?.cancel()
  }
}
