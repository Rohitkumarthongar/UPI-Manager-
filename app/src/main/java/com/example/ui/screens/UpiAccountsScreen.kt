package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import com.example.ui.components.FintechCard
import com.example.ui.components.StatusTag
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.util.LimitValidationResult
import com.example.util.QrGenerator
import com.example.util.UpiAccountLimitInfo
import com.example.util.UpiLimitCalculator

@Composable
fun UpiAccountsScreen(
  accounts: List<UpiAccount>,
  activeAccount: UpiAccount?,
  transactions: List<TransactionItem>,
  onSwitchAccount: (Long) -> Unit,
  onAddAccount: (
    upiId: String,
    payeeName: String,
    label: String,
    bankName: String,
    monthlyLimit: Double,
    quarterlyLimit: Double,
    isEnforced: Boolean
  ) -> Unit,
  onUpdateAccountLimits: (
    account: UpiAccount,
    monthlyLimit: Double,
    quarterlyLimit: Double,
    isEnforced: Boolean
  ) -> Unit,
  onDeleteAccount: (UpiAccount) -> Unit,
  onOpenMultiDeviceSync: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var customAmountText by remember { mutableStateOf("") }
  var showAddDialog by remember { mutableStateOf(false) }
  var accountToEditLimits by remember { mutableStateOf<UpiAccount?>(null) }
  var accountToDelete by remember { mutableStateOf<UpiAccount?>(null) }
  var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

  val currentActive = activeAccount ?: accounts.firstOrNull()

  // Live Limit Info for the currently active account
  val activeLimitInfo: UpiAccountLimitInfo? = currentActive?.let {
    UpiLimitCalculator.calculateLimitInfo(it, transactions)
  }

  // Live validation for requested amount on active account
  val requestedAmount = customAmountText.toDoubleOrNull() ?: 0.0
  val validationResult: LimitValidationResult = if (currentActive != null && requestedAmount > 0.0) {
    UpiLimitCalculator.validateIncomingAmount(
      account = currentActive,
      amount = requestedAmount,
      transactions = transactions,
      allAccounts = accounts
    )
  } else {
    LimitValidationResult.Allowed
  }

  // Generate QR bitmap reactively when active account or custom amount changes
  LaunchedEffect(currentActive, customAmountText) {
    if (currentActive != null) {
      val amt = customAmountText.toDoubleOrNull()
      val payload = QrGenerator.buildUpiPayload(
        upiId = currentActive.upiId,
        payeeName = currentActive.payeeName,
        amount = amt,
        note = "Payment via Offline Ledger"
      )
      qrBitmap = QrGenerator.generateQrBitmap(payload, sizePx = 512)
    } else {
      qrBitmap = null
    }
  }

  BoxWithConstraints(modifier = modifier.fillMaxSize().testTag("upi_accounts_screen")) {
    val isWideScreen = maxWidth >= 600.dp

    if (isWideScreen) {
      // -------------------------------------------------------------
      // RESPONSIVE EXPANDED / TABLET DUAL PANE LAYOUT
      // -------------------------------------------------------------
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
      ) {
        // Left Column: Active QR Presenter & Live Quota Gauge
        Column(
          modifier = Modifier
            .weight(0.48f)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          if (currentActive != null && activeLimitInfo != null) {
            ActiveQrCard(
              activeAccount = currentActive,
              limitInfo = activeLimitInfo,
              qrBitmap = qrBitmap,
              customAmountText = customAmountText,
              onAmountChange = { customAmountText = it },
              validationResult = validationResult,
              onSwitchAlternative = { onSwitchAccount(it.id) },
              onCopyUpi = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", currentActive.upiId))
                Toast.makeText(context, "UPI ID copied to clipboard", Toast.LENGTH_SHORT).show()
              },
              onShareQr = {
                if (validationResult is LimitValidationResult.Blocked && currentActive.isLimitEnforced) {
                  Toast.makeText(context, "Cannot share: Transaction exceeds receiving quota restriction", Toast.LENGTH_LONG).show()
                } else {
                  val payload = QrGenerator.buildUpiPayload(
                    upiId = currentActive.upiId,
                    payeeName = currentActive.payeeName,
                    amount = customAmountText.toDoubleOrNull(),
                    note = "Pay ${currentActive.payeeName}"
                  )
                  val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Scan or pay to UPI: $payload")
                    type = "text/plain"
                  }
                  context.startActivity(Intent.createChooser(sendIntent, "Share UPI QR Payload"))
                }
              }
            )
          } else {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth().padding(24.dp)
            ) {
              Text("No active UPI account selected.", modifier = Modifier.padding(16.dp))
            }
          }
        }

        // Right Column: Configured Accounts & Limit Restrictions
        Column(
          modifier = Modifier
            .weight(0.52f)
            .fillMaxHeight()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Configured UPI Accounts (${accounts.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Monthly & Quarterly Limits Active",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Button(
              onClick = { showAddDialog = true },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("button_add_account_wide")
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Add UPI")
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Multi-Device Range Sync Banner
          RangeSyncBanner(onOpen = onOpenMultiDeviceSync)

          Spacer(modifier = Modifier.height(12.dp))

          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
          ) {
            items(accounts, key = { it.id }) { acc ->
              val info = UpiLimitCalculator.calculateLimitInfo(acc, transactions)
              UpiAccountLimitCard(
                account = acc,
                info = info,
                isActive = currentActive?.id == acc.id,
                onSwitch = { onSwitchAccount(acc.id) },
                onEditLimits = { accountToEditLimits = acc },
                onDelete = { accountToDelete = acc }
              )
            }
          }
        }
      }
    } else {
      // -------------------------------------------------------------
      // RESPONSIVE COMPACT PHONE LAYOUT
      // -------------------------------------------------------------
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // 0. Range Sync Banner
        item {
          RangeSyncBanner(onOpen = onOpenMultiDeviceSync)
        }

        // 1. Account Switcher Carousel
        item {
          Column {
            Text(
              text = "Switch Receiving Account",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (accounts.isEmpty()) {
              Text(
                "No UPI accounts added yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(accounts, key = { it.id }) { acc ->
                  val isSelected = currentActive?.id == acc.id
                  val accInfo = UpiLimitCalculator.calculateLimitInfo(acc, transactions)
                  Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                      .clip(RoundedCornerShape(14.dp))
                      .clickable { onSwitchAccount(acc.id) }
                      .testTag("upi_switch_${acc.id}")
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      if (isSelected) {
                        Icon(
                          Icons.Default.CheckCircle,
                          contentDescription = null,
                          tint = EmeraldDark,
                          modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                      }
                      Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Text(
                            text = acc.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                          )
                          if (accInfo.isRestricted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                              modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentRose)
                            )
                          }
                        }
                        Text(
                          text = acc.upiId,
                          style = MaterialTheme.typography.labelSmall,
                          color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // 2. Active Receiving QR & Dynamic Validation Card
        if (currentActive != null && activeLimitInfo != null) {
          item {
            ActiveQrCard(
              activeAccount = currentActive,
              limitInfo = activeLimitInfo,
              qrBitmap = qrBitmap,
              customAmountText = customAmountText,
              onAmountChange = { customAmountText = it },
              validationResult = validationResult,
              onSwitchAlternative = { onSwitchAccount(it.id) },
              onCopyUpi = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", currentActive.upiId))
                Toast.makeText(context, "UPI ID copied to clipboard", Toast.LENGTH_SHORT).show()
              },
              onShareQr = {
                if (validationResult is LimitValidationResult.Blocked && currentActive.isLimitEnforced) {
                  Toast.makeText(context, "Cannot share: Transaction exceeds receiving quota restriction", Toast.LENGTH_LONG).show()
                } else {
                  val payload = QrGenerator.buildUpiPayload(
                    upiId = currentActive.upiId,
                    payeeName = currentActive.payeeName,
                    amount = customAmountText.toDoubleOrNull(),
                    note = "Pay ${currentActive.payeeName}"
                  )
                  val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Scan or pay to UPI: $payload")
                    type = "text/plain"
                  }
                  context.startActivity(Intent.createChooser(sendIntent, "Share UPI QR Payload"))
                }
              }
            )
          }
        }

        // 3. Header for Configured Accounts
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Configured UPI Accounts (${accounts.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Tap 'Set Limits' to configure monthly & quarterly caps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // 4. Accounts List with Limit Gauges
        items(accounts, key = { it.id }) { acc ->
          val info = UpiLimitCalculator.calculateLimitInfo(acc, transactions)
          UpiAccountLimitCard(
            account = acc,
            info = info,
            isActive = currentActive?.id == acc.id,
            onSwitch = { onSwitchAccount(acc.id) },
            onEditLimits = { accountToEditLimits = acc },
            onDelete = { accountToDelete = acc }
          )
        }
      }

      // Add Account FAB for Compact Mode
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
          .testTag("fab_add_upi_account")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Add, contentDescription = "Add")
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add UPI ID", fontWeight = FontWeight.Bold)
        }
      }
    }
  }

  // -------------------------------------------------------------
  // DIALOGS: ADD ACCOUNT & EDIT LIMITS
  // -------------------------------------------------------------

  // Add UPI Account Dialog
  if (showAddDialog) {
    AddUpiAccountDialog(
      onDismiss = { showAddDialog = false },
      onConfirm = { upiId, payeeName, label, bankName, mLimit, qLimit, isEnforced ->
        onAddAccount(upiId, payeeName, label, bankName, mLimit, qLimit, isEnforced)
        showAddDialog = false
      }
    )
  }

  // Edit UPI Limits Dialog
  if (accountToEditLimits != null) {
    val acc = accountToEditLimits!!
    val info = UpiLimitCalculator.calculateLimitInfo(acc, transactions)
    EditUpiLimitsDialog(
      account = acc,
      limitInfo = info,
      onDismiss = { accountToEditLimits = null },
      onSave = { newMonthlyLimit, newQuarterlyLimit, isEnforced ->
        onUpdateAccountLimits(acc, newMonthlyLimit, newQuarterlyLimit, isEnforced)
        accountToEditLimits = null
        Toast.makeText(context, "Updated limits for ${acc.label}", Toast.LENGTH_SHORT).show()
      }
    )
  }

  // Confirm Delete Account Dialog
  if (accountToDelete != null) {
    AlertDialog(
      onDismissRequest = { accountToDelete = null },
      title = { Text("Delete UPI Account") },
      text = { Text("Remove '${accountToDelete!!.label}' (${accountToDelete!!.upiId}) from receiving accounts?") },
      confirmButton = {
        Button(
          onClick = {
            onDeleteAccount(accountToDelete!!)
            accountToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = AccentRose)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { accountToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

/**
 * Visual QR Presentation card with dynamic amount input, real-time limit status, and restriction alert.
 */
@Composable
fun ActiveQrCard(
  activeAccount: UpiAccount,
  limitInfo: UpiAccountLimitInfo,
  qrBitmap: Bitmap?,
  customAmountText: String,
  onAmountChange: (String) -> Unit,
  validationResult: LimitValidationResult,
  onSwitchAlternative: (UpiAccount) -> Unit,
  onCopyUpi: () -> Unit,
  onShareQr: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .padding(20.dp)
        .fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        StatusTag(text = "ACTIVE RECEIVING QR", color = EmeraldDark)

        if (activeAccount.isLimitEnforced) {
          StatusTag(text = "RESTRICTION ON", color = MaterialTheme.colorScheme.primary)
        } else {
          StatusTag(text = "MONITORING ONLY", color = MaterialTheme.colorScheme.outline)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = activeAccount.payeeName,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = activeAccount.upiId,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(14.dp))

      // QR Code Box with dynamic overlay if restricted
      Box(
        modifier = Modifier
          .size(230.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color.White)
          .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
          .padding(10.dp),
        contentAlignment = Alignment.Center
      ) {
        if (qrBitmap != null) {
          Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "UPI QR Code",
            modifier = Modifier.fillMaxSize().testTag("active_upi_qr_image")
          )
        } else {
          Icon(
            Icons.Default.QrCode,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline
          )
        }

        // Overlay if limit is breached and strictly enforced
        if (validationResult is LimitValidationResult.Blocked && activeAccount.isLimitEnforced) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.75f))
              .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.Block, contentDescription = null, tint = AccentRose, modifier = Modifier.size(36.dp))
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                "LIMIT EXCEEDED",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
              )
              Text(
                "Cannot accept ₹$customAmountText",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Dynamic Request Amount Input
      OutlinedTextField(
        value = customAmountText,
        onValueChange = onAmountChange,
        label = { Text("Request Specific Amount (₹ Optional)") },
        placeholder = { Text("e.g. 1500") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(0.9f).testTag("qr_amount_input")
      )

      // Dynamic Warning / Restriction Feedback
      when (validationResult) {
        is LimitValidationResult.Blocked -> {
          Spacer(modifier = Modifier.height(10.dp))
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = AccentRose.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRose, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "${validationResult.period} Limit Exceeded",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = AccentRose
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Attempted: ${formatCurrency(validationResult.attemptedAmount)}. Cap: ${formatCurrency(validationResult.limit)}. Remaining quota: ${formatCurrency(validationResult.remaining)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
              )

              if (validationResult.alternativeAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Suggested alternatives with available limit:",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  items(validationResult.alternativeAccounts) { alt ->
                    OutlinedButton(
                      onClick = { onSwitchAlternative(alt) },
                      shape = RoundedCornerShape(8.dp),
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                      modifier = Modifier.height(32.dp)
                    ) {
                      Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Switch to ${alt.label}", style = MaterialTheme.typography.labelSmall)
                    }
                  }
                }
              }
            }
          }
        }
        is LimitValidationResult.WarningOnly -> {
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = AccentAmber.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = validationResult.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
        else -> {}
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Compact Live Quota Gauge on Active Account
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          // Monthly Progress
          QuotaProgressBar(
            periodLabel = "Month (${UpiLimitCalculator.getCurrentMonthName()})",
            limit = limitInfo.monthlyLimit,
            used = limitInfo.monthlyUsed,
            remaining = limitInfo.monthlyRemaining,
            percent = limitInfo.monthlyPercent,
            isExceeded = limitInfo.isMonthlyExceeded
          )

          // Quarterly Progress
          QuotaProgressBar(
            periodLabel = "Quarter (${UpiLimitCalculator.getCurrentQuarterName()})",
            limit = limitInfo.quarterlyLimit,
            used = limitInfo.quarterlyUsed,
            remaining = limitInfo.quarterlyRemaining,
            percent = limitInfo.quarterlyPercent,
            isExceeded = limitInfo.isQuarterlyExceeded
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = onCopyUpi,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy ID")
        }

        Button(
          onClick = onShareQr,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (validationResult is LimitValidationResult.Blocked && activeAccount.isLimitEnforced) AccentRose else MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier.weight(1f)
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (validationResult is LimitValidationResult.Blocked && activeAccount.isLimitEnforced) "Restricted" else "Share QR")
        }
      }
    }
  }
}

/**
 * Individual UPI Account Card with real-time monthly & quarterly limit status, configuration button,
 * and switcher.
 */
@Composable
fun UpiAccountLimitCard(
  account: UpiAccount,
  info: UpiAccountLimitInfo,
  isActive: Boolean,
  onSwitch: () -> Unit,
  onEditLimits: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  FintechCard(modifier = modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Header: Bank/Label, Status, and Switch button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.AccountBalance,
              contentDescription = null,
              tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(account.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
              if (isActive) {
                Spacer(modifier = Modifier.width(6.dp))
                StatusTag(text = "ACTIVE", color = EmeraldDark)
              }
            }
            Text(account.upiId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (account.bankName.isNotBlank()) {
              Text(account.bankName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!isActive) {
            TextButton(onClick = onSwitch, shape = RoundedCornerShape(8.dp)) {
              Text("Set Active", style = MaterialTheme.typography.labelMedium)
            }
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
          }
        }
      }

      // Monthly & Quarterly Limit Progress Bars
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
          .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        QuotaProgressBar(
          periodLabel = "Monthly Limit",
          limit = info.monthlyLimit,
          used = info.monthlyUsed,
          remaining = info.monthlyRemaining,
          percent = info.monthlyPercent,
          isExceeded = info.isMonthlyExceeded
        )

        QuotaProgressBar(
          periodLabel = "Quarterly Limit",
          limit = info.quarterlyLimit,
          used = info.quarterlyUsed,
          remaining = info.quarterlyRemaining,
          percent = info.quarterlyPercent,
          isExceeded = info.isQuarterlyExceeded
        )
      }

      // Bottom Row: Strict Enforcement indicator and "Edit Limits" action
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (account.isLimitEnforced) Icons.Default.Lock else Icons.Default.Warning,
            contentDescription = null,
            tint = if (account.isLimitEnforced) EmeraldDark else AccentAmber,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (account.isLimitEnforced) "Strict: Block Over-limit" else "Soft: Warn Only",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        OutlinedButton(
          onClick = onEditLimits,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
          modifier = Modifier.height(34.dp).testTag("edit_limits_btn_${account.id}")
        ) {
          Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Configure Limits", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
  }
}

/**
 * Reusable progress indicator bar for Monthly and Quarterly quota limits.
 */
@Composable
fun QuotaProgressBar(
  periodLabel: String,
  limit: Double,
  used: Double,
  remaining: Double,
  percent: Float,
  isExceeded: Boolean
) {
  val barColor = when {
    isExceeded -> AccentRose
    percent >= 0.8f -> AccentAmber
    else -> EmeraldPrimary
  }

  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = periodLabel,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
      )
      if (limit > 0.0) {
        Text(
          text = "${formatCurrency(used)} / ${formatCurrency(limit)}",
          style = MaterialTheme.typography.labelSmall,
          color = if (isExceeded) AccentRose else MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        Text(
          text = "No limit (Unlimited)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.outline
        )
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    if (limit > 0.0) {
      LinearProgressIndicator(
        progress = { percent },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        color = barColor,
        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
      )
      Spacer(modifier = Modifier.height(2.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = if (isExceeded) "Limit breached by ${formatCurrency(used - limit)}" else "Remaining: ${formatCurrency(remaining)}",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = if (isExceeded) AccentRose else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "${(percent * 100).toInt()}%",
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
          color = barColor
        )
      }
    } else {
      LinearProgressIndicator(
        progress = { 0f },
        modifier = Modifier
          .fillMaxWidth()
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp)),
        color = MaterialTheme.colorScheme.outline,
        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
      )
    }
  }
}

/**
 * Dialog to configure Monthly and Quarterly receiving limits and strict restriction mode for a UPI account.
 */
@Composable
fun EditUpiLimitsDialog(
  account: UpiAccount,
  limitInfo: UpiAccountLimitInfo,
  onDismiss: () -> Unit,
  onSave: (monthlyLimit: Double, quarterlyLimit: Double, isEnforced: Boolean) -> Unit
) {
  var monthlyText by remember {
    mutableStateOf(if (account.monthlyLimit > 0) account.monthlyLimit.toInt().toString() else "")
  }
  var quarterlyText by remember {
    mutableStateOf(if (account.quarterlyLimit > 0) account.quarterlyLimit.toInt().toString() else "")
  }
  var isStrict by remember { mutableStateOf(account.isLimitEnforced) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text("UPI Receiving Limits", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(
          text = "${account.label} (${account.upiId})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Current Utilization Info Card
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text("Current Accepted Transactions:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            Text("• This Month: ${formatCurrency(limitInfo.monthlyUsed)}", style = MaterialTheme.typography.labelSmall)
            Text("• This Quarter: ${formatCurrency(limitInfo.quarterlyUsed)}", style = MaterialTheme.typography.labelSmall)
          }
        }

        // 1. Monthly Limit Field
        Column {
          OutlinedTextField(
            value = monthlyText,
            onValueChange = { monthlyText = it },
            label = { Text("Monthly Limit (₹)") },
            placeholder = { Text("e.g. 50000 (0 = unlimited)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("edit_monthly_limit_input")
          )
          Spacer(modifier = Modifier.height(6.dp))
          // Quick presets
          Text("Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(25000, 50000, 100000, 200000)) { p ->
              FilterChip(
                selected = monthlyText == p.toString(),
                onClick = { monthlyText = p.toString() },
                label = { Text("₹${p / 1000}k") }
              )
            }
            item {
              FilterChip(
                selected = monthlyText == "" || monthlyText == "0",
                onClick = { monthlyText = "0" },
                label = { Text("None") }
              )
            }
          }
        }

        // 2. Quarterly Limit Field
        Column {
          OutlinedTextField(
            value = quarterlyText,
            onValueChange = { quarterlyText = it },
            label = { Text("Quarterly Limit (₹)") },
            placeholder = { Text("e.g. 150000 (0 = unlimited)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("edit_quarterly_limit_input")
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text("Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(75000, 150000, 300000, 500000)) { p ->
              FilterChip(
                selected = quarterlyText == p.toString(),
                onClick = { quarterlyText = p.toString() },
                label = { Text("₹${p / 1000}k") }
              )
            }
            item {
              FilterChip(
                selected = quarterlyText == "" || quarterlyText == "0",
                onClick = { quarterlyText = "0" },
                label = { Text("None") }
              )
            }
          }
        }

        // 3. Strict Restriction Enforcement Toggle
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              "Enforce Strict Restriction",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
              "Reject or block transactions that exceed monthly or quarterly allowance",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Switch(
            checked = isStrict,
            onCheckedChange = { isStrict = it },
            modifier = Modifier.testTag("strict_enforcement_switch")
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val mLimit = monthlyText.toDoubleOrNull() ?: 0.0
          val qLimit = quarterlyText.toDoubleOrNull() ?: 0.0
          onSave(mLimit, qLimit, isStrict)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.testTag("save_limits_button")
      ) {
        Text("Save Restrictions")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

/**
 * Dialog to add a new UPI receiving account with initial limits.
 */
@Composable
fun AddUpiAccountDialog(
  onDismiss: () -> Unit,
  onConfirm: (
    upiId: String,
    payeeName: String,
    label: String,
    bankName: String,
    monthlyLimit: Double,
    quarterlyLimit: Double,
    isEnforced: Boolean
  ) -> Unit
) {
  var newUpiId by remember { mutableStateOf("") }
  var newPayeeName by remember { mutableStateOf("") }
  var newLabel by remember { mutableStateOf("") }
  var newBankName by remember { mutableStateOf("") }
  var newMonthlyLimit by remember { mutableStateOf("") }
  var newQuarterlyLimit by remember { mutableStateOf("") }
  var isStrict by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add UPI Account") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = newUpiId,
          onValueChange = { newUpiId = it },
          label = { Text("UPI ID / VPA") },
          placeholder = { Text("e.g. business@okhdfcbank") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("input_new_upi_id")
        )
        OutlinedTextField(
          value = newPayeeName,
          onValueChange = { newPayeeName = it },
          label = { Text("Payee / Business Name") },
          placeholder = { Text("e.g. Sharma Hardware") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("input_new_payee_name")
        )
        OutlinedTextField(
          value = newLabel,
          onValueChange = { newLabel = it },
          label = { Text("Account Label") },
          placeholder = { Text("e.g. Current Account, Shop QR") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("input_new_account_label")
        )
        OutlinedTextField(
          value = newBankName,
          onValueChange = { newBankName = it },
          label = { Text("Bank Name (Optional)") },
          placeholder = { Text("e.g. HDFC Bank, ICICI") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = newMonthlyLimit,
          onValueChange = { newMonthlyLimit = it },
          label = { Text("Monthly Receiving Limit (₹ Optional)") },
          placeholder = { Text("e.g. 50000 (0 = unlimited)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = newQuarterlyLimit,
          onValueChange = { newQuarterlyLimit = it },
          label = { Text("Quarterly Receiving Limit (₹ Optional)") },
          placeholder = { Text("e.g. 150000 (0 = unlimited)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Strict Restriction", style = MaterialTheme.typography.bodySmall)
          Switch(checked = isStrict, onCheckedChange = { isStrict = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (newUpiId.isNotBlank() && newPayeeName.isNotBlank()) {
            onConfirm(
              newUpiId,
              newPayeeName,
              newLabel.ifBlank { "UPI Account" },
              newBankName,
              newMonthlyLimit.toDoubleOrNull() ?: 0.0,
              newQuarterlyLimit.toDoubleOrNull() ?: 0.0,
              isStrict
            )
          }
        },
        enabled = newUpiId.isNotBlank() && newPayeeName.isNotBlank(),
        modifier = Modifier.testTag("submit_add_upi_button")
      ) {
        Text("Add Account")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
private fun RangeSyncBanner(onOpen: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable { onOpen() }
      .testTag("multi_device_sync_banner")
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            Icons.Default.WifiTethering,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "Multi-Device Range Sync",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Using same UPI on multiple phones? Sync transactions & limits offline within Wi-Fi/Hotspot range.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = onOpen,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(34.dp)
      ) {
        Text("Sync Hub", style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}
