package com.example.ui.dialogs

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.Client
import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import com.example.ocr.ParsedReceipt
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.util.LimitValidationResult
import com.example.util.UpiLimitCalculator

@Composable
fun AddTransactionDialog(
  upiAccounts: List<UpiAccount>,
  clients: List<Client>,
  transactions: List<TransactionItem> = emptyList(),
  prefilledReceipt: ParsedReceipt? = null,
  isOcrLoading: Boolean = false,
  onScanReceiptRequest: ((Bitmap) -> Unit)? = null,
  onDismiss: () -> Unit,
  onSave: (
    amount: Double,
    type: String,
    category: String,
    accountId: Long?,
    clientId: Long?,
    note: String,
    vendor: String?,
    source: String
  ) -> Unit
) {
  val context = LocalContext.current

  var amountText by remember {
    mutableStateOf(prefilledReceipt?.amount?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
  }
  var type by remember { mutableStateOf(prefilledReceipt?.type ?: "EXPENSE") }
  var selectedCategory by remember {
    mutableStateOf(prefilledReceipt?.suggestedCategory ?: "Sales")
  }
  var selectedAccountId by remember {
    mutableStateOf<Long?>(upiAccounts.firstOrNull { it.isActive }?.id ?: upiAccounts.firstOrNull()?.id)
  }
  var selectedClientId by remember { mutableStateOf<Long?>(null) }
  var note by remember {
    mutableStateOf(
      if (prefilledReceipt != null) {
        val ref = prefilledReceipt.invoiceNo?.let { "Ref/UTR: $it" }
        val upi = prefilledReceipt.upiId?.let { "VPA: $it" }
        listOfNotNull(ref, upi).joinToString(" • ").ifBlank { "Scanned Payment Receipt" }
      } else ""
    )
  }
  var vendor by remember { mutableStateOf(prefilledReceipt?.vendor ?: "") }
  var source by remember { mutableStateOf(if (prefilledReceipt != null) "OCR" else "MANUAL") }

  // Gallery image picker launcher for OCR
  val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri?.let {
      try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
          }
        } else {
          @Suppress("DEPRECATION")
          MediaStore.Images.Media.getBitmap(context.contentResolver, it)
        }
        onScanReceiptRequest?.invoke(bitmap)
      } catch (_: Exception) {}
    }
  }

  // Camera capture launcher for OCR
  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap: Bitmap? ->
    bitmap?.let {
      onScanReceiptRequest?.invoke(it)
    }
  }

  // Camera permission launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      cameraLauncher.launch(null)
    } else {
      Toast.makeText(context, "Camera permission is required to capture payment screenshots & receipts", Toast.LENGTH_SHORT).show()
    }
  }

  val categories = listOf("Sales", "Services", "Inventory", "Rent", "Utilities", "Food", "Travel", "Salary", "Supplies", "Other")

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .padding(vertical = 24.dp)
        .testTag("add_transaction_dialog")
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = if (source == "OCR") "Confirm OCR Receipt" else "New Transaction",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Encrypted",
                tint = EmeraldPrimary,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "AES-256 Encrypted at Rest",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldDark
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // OCR Scanner Quick Trigger Buttons
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = "OCR Scanner",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Smart Receipt OCR",
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = if (isOcrLoading) "Analyzing receipt..." else "Extract amount, vendor & date",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            if (isOcrLoading) {
              CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
              Row {
                IconButton(
                  onClick = {
                    val hasCamPermission = ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCamPermission) {
                      cameraLauncher.launch(null)
                    } else {
                      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                  },
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .testTag("ocr_camera_button")
                ) {
                  Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Camera OCR",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                  onClick = { galleryLauncher.launch("image/*") },
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .testTag("ocr_gallery_button")
                ) {
                  Icon(
                    Icons.Default.Photo,
                    contentDescription = "Gallery OCR",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Type Toggle: Income vs Expense
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = { type = "EXPENSE" },
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = if (type == "EXPENSE") AccentRose.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
              contentColor = if (type == "EXPENSE") AccentRose else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).testTag("type_expense_button")
          ) {
            Text("Expense / Paid", fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = { type = "INCOME" },
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = if (type == "INCOME") EmeraldPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
              contentColor = if (type == "INCOME") EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).testTag("type_income_button")
          ) {
            Text("Income / Received", fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Amount Input Field
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text("Amount (₹)") },
          placeholder = { Text("0.00") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("amount_input_field")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Vendor / Merchant Name
        OutlinedTextField(
          value = vendor,
          onValueChange = { vendor = it },
          label = { Text("Vendor / Payer Name") },
          placeholder = { Text("e.g., Apex Stores, Chai Point") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("vendor_input_field")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Category Selection
        Text(
          text = "Category",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(categories) { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { selectedCategory = cat },
              label = { Text(cat) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Account Link (UPI Accounts)
        val parsedAmt = amountText.toDoubleOrNull() ?: 0.0
        val selectedAccount = upiAccounts.find { it.id == selectedAccountId }
        val upiValidation = if (type == "INCOME" && selectedAccount != null && parsedAmt > 0.0) {
          UpiLimitCalculator.validateIncomingAmount(
            account = selectedAccount,
            amount = parsedAmt,
            transactions = transactions,
            allAccounts = upiAccounts
          )
        } else {
          LimitValidationResult.Allowed
        }
        val isBlockedByLimit = upiValidation is LimitValidationResult.Blocked && (selectedAccount?.isLimitEnforced == true)

        if (upiAccounts.isNotEmpty()) {
          Text(
            text = "Linked UPI Account",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(upiAccounts) { acc ->
              val info = UpiLimitCalculator.calculateLimitInfo(acc, transactions)
              FilterChip(
                selected = selectedAccountId == acc.id,
                onClick = { selectedAccountId = acc.id },
                label = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(acc.label)
                    if (acc.isLimitEnforced && info.isRestricted) {
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("(Limit Reached)", color = AccentRose, style = MaterialTheme.typography.labelSmall)
                    }
                  }
                }
              )
            }
          }

          // Live Limit Warning / Restriction Banner
          when (upiValidation) {
            is LimitValidationResult.Blocked -> {
              Spacer(modifier = Modifier.height(8.dp))
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentRose.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text(
                    text = "⚠️ ${upiValidation.period} Limit Exceeded for ${selectedAccount?.label}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AccentRose
                  )
                  Text(
                    text = "Attempted: ${formatCurrency(upiValidation.attemptedAmount)} • Remaining quota: ${formatCurrency(upiValidation.remaining)} (Exceeds by ${formatCurrency(upiValidation.excess)}).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  if (upiValidation.alternativeAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = "Switch to account with available quota:",
                      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      items(upiValidation.alternativeAccounts) { alt ->
                        OutlinedButton(
                          onClick = { selectedAccountId = alt.id },
                          shape = RoundedCornerShape(8.dp),
                          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                          modifier = Modifier.height(28.dp)
                        ) {
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
                Text(
                  text = "⚠️ Notice: ${upiValidation.message}",
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(8.dp),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
            else -> {}
          }

          Spacer(modifier = Modifier.height(14.dp))
        }

        // Client Link (Optional for B2B Client Ledger)
        if (clients.isNotEmpty()) {
          Text(
            text = "Link to Client Ledger (Optional)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              FilterChip(
                selected = selectedClientId == null,
                onClick = { selectedClientId = null },
                label = { Text("None (General)") }
              )
            }
            items(clients) { client ->
              FilterChip(
                selected = selectedClientId == client.id,
                onClick = { selectedClientId = client.id },
                label = { Text(client.name) }
              )
            }
          }
          Spacer(modifier = Modifier.height(14.dp))
        }

        // Notes / Description
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("Note / Description (Encrypted)") },
          placeholder = { Text("Details for invoice or transaction") },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("note_input_field")
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Save Button
        Button(
          onClick = {
            val amt = amountText.toDoubleOrNull() ?: 0.0
            if (amt > 0 && !isBlockedByLimit) {
              onSave(
                amt,
                type,
                selectedCategory,
                selectedAccountId,
                selectedClientId,
                note,
                vendor.ifBlank { null },
                source
              )
              onDismiss()
            }
          },
          enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0 && !isBlockedByLimit,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isBlockedByLimit) AccentRose else MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("save_transaction_button")
        ) {
          Text(
            text = when {
              isBlockedByLimit -> "Blocked: Exceeds UPI Quota Limit"
              source == "OCR" -> "Confirm & Log Transaction"
              else -> "Save Transaction"
            },
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}
