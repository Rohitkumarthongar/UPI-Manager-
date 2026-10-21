package com.example.ui.dialogs

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.UpiAccount
import com.example.payments.UpiApp
import com.example.payments.UpiPay
import com.example.payments.UpiPayRequest
import com.example.payments.UpiPayResult
import com.example.ui.components.formatCurrency
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Locale

/**
 * Scan & Pay: opens the camera to read a UPI QR, then hands the payment to an installed UPI app the user picks
 * (GPay, PhonePe, Paytm, BHIM…). A successful payment is logged to the ledger as an expense with its UPI reference.
 */
@Composable
fun ScanPayFlow(
  upiAccounts: List<UpiAccount>,
  onPaid: (amount: Double, payeeName: String, payeeVpa: String, reference: String?, accountId: Long?, note: String) -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  var request by remember { mutableStateOf<UpiPayRequest?>(null) }

  fun accept(raw: String?) {
    val parsed = raw?.let(UpiPay::parse)
    if (parsed == null) {
      Toast.makeText(context, if (raw == null) "No QR code found in that image" else "That QR is not a UPI payment code", Toast.LENGTH_LONG).show()
      onClose()
    } else request = parsed
  }

  // Fallback when the Google camera scanner is unavailable: read the QR from a photo or screenshot.
  val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    if (uri == null) onClose() else accept(decodeBitmap(context, uri)?.let(UpiPay::decodeQr))
  }

  LaunchedEffect(Unit) {
    if (request != null) return@LaunchedEffect
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
    GmsBarcodeScanning.getClient(context, options).startScan()
      .addOnSuccessListener { accept(it.rawValue) }
      .addOnCanceledListener { onClose() }
      .addOnFailureListener { e ->
        // Backing out of the scanner also ends here ("Failed to scan code"): only fall back to picking a QR image
        // when the Google scanner itself is missing.
        val unavailable = (e as? MlKitException)?.errorCode in setOf(
          MlKitException.UNAVAILABLE, MlKitException.CODE_SCANNER_UNAVAILABLE,
          MlKitException.CODE_SCANNER_APP_NAME_UNAVAILABLE, MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD
        )
        if (!unavailable) {
          onClose()
          return@addOnFailureListener
        }
        Toast.makeText(context, "Camera scanner unavailable. Pick a photo or screenshot of the QR instead.", Toast.LENGTH_LONG).show()
        pickImage.launch("image/*")
      }
  }

  request?.let { req -> PayDialog(req, upiAccounts, onPaid, onClose) }
}

@Composable
private fun PayDialog(
  req: UpiPayRequest,
  upiAccounts: List<UpiAccount>,
  onPaid: (Double, String, String, String?, Long?, String) -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val apps = remember { UpiPay.installedApps(context) }
  var amountText by remember { mutableStateOf(req.amount?.let { String.format(Locale.US, "%.2f", it) } ?: "") }
  var note by remember { mutableStateOf(req.note.orEmpty()) }
  var accountId by remember { mutableStateOf(upiAccounts.firstOrNull { it.isActive }?.id ?: upiAccounts.firstOrNull()?.id) }
  var askOutcome by remember { mutableStateOf(false) }
  val amount = amountText.toDoubleOrNull()

  fun record(reference: String?) {
    val amt = amount ?: return
    onPaid(amt, req.payeeName, req.payeeVpa, reference, accountId, note.ifBlank { "UPI payment to ${req.payeeName}" })
    Toast.makeText(context, "Paid ${formatCurrency(amt)} to ${req.payeeName} — logged to ledger", Toast.LENGTH_SHORT).show()
    onClose()
  }

  val payLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    when (val r = UpiPay.parseResult(result.data)) {
      is UpiPayResult.Success -> record(r.reference)
      is UpiPayResult.Failed -> Toast.makeText(context, r.reason, Toast.LENGTH_LONG).show()
      UpiPayResult.Unknown -> askOutcome = true
    }
  }

  fun payWith(app: UpiApp) {
    if ((amount ?: 0.0) <= 0) {
      Toast.makeText(context, "Enter the amount to pay", Toast.LENGTH_SHORT).show()
      return
    }
    runCatching { payLauncher.launch(UpiPay.payIntent(req.toUri(amount, note), app.packageName)) }
      .onFailure { Toast.makeText(context, "Couldn't open ${app.label}", Toast.LENGTH_SHORT).show() }
  }

  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 24.dp)) {
      Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Pay ${req.payeeName}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(req.payeeVpa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
          enabled = !req.amountLocked,
          label = { Text(if (req.amountLocked) "Amount (fixed by QR)" else "Amount (₹)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )
        if (!req.params.containsKey("sign")) {
          OutlinedTextField(
            value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
          )
        }

        if (upiAccounts.isNotEmpty()) {
          Text("Track against account", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            upiAccounts.take(3).forEach { acc ->
              FilterChip(selected = accountId == acc.id, onClick = { accountId = acc.id }, label = { Text(acc.label, maxLines = 1) })
            }
          }
        }

        Text("Pay with", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        if (apps.isEmpty()) {
          Text(
            "No UPI app found on this phone. Install Google Pay, PhonePe, Paytm or BHIM to pay.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
          )
        }
        apps.forEach { app ->
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { payWith(app) }
          ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
              val icon = remember(app.packageName) { app.icon?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() } }
              if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(32.dp))
              else Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(32.dp))
              Spacer(Modifier.width(12.dp))
              Text(app.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
              Text(amount?.let { formatCurrency(it) } ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
        Spacer(Modifier.height(4.dp))
      }
    }
  }

  if (askOutcome) {
    AlertDialog(
      onDismissRequest = { askOutcome = false },
      title = { Text("Did the payment go through?") },
      text = { Text("The UPI app didn't report the result. Check it, then confirm so the ledger stays correct.") },
      confirmButton = { TextButton(onClick = { askOutcome = false; record(null) }) { Text("Yes, it's paid") } },
      dismissButton = { TextButton(onClick = { askOutcome = false }) { Text("No") } }
    )
  }
}

/** Decodes a picked image as a software bitmap (hardware bitmaps can't be read pixel by pixel). */
fun decodeBitmap(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
  } else {
    @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
  }
}.getOrNull()
