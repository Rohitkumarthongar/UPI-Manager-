package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.payments.PaymentMessageParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class ParsedReceipt(
  val amount: Double?,
  val vendor: String?,
  val dateString: String?,
  val invoiceNo: String?,
  val suggestedCategory: String,
  val rawText: String,
  val confidenceScore: Float = 0.9f,
  /** "INCOME"/"EXPENSE" when the image is a UPI payment screenshot, null for a shop receipt. */
  val type: String? = null,
  val referenceNumber: String? = null,
  val fromParty: String? = null,
  val toParty: String? = null,
  val vpas: List<String> = emptyList()
) {
  val isUpiScreenshot: Boolean get() = type != null
}

object ReceiptOcrHelper {

  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  /** Reads a photo (camera or gallery) at full resolution; EXIF rotation is applied. Null when no text is found. */
  suspend fun parseReceiptImage(context: Context, uri: Uri): ParsedReceipt? =
    runCatching { recognize(InputImage.fromFilePath(context, uri)) }.getOrNull()?.let(::extractOrNull)

  suspend fun parseReceiptImage(bitmap: Bitmap): ParsedReceipt? =
    runCatching { recognize(InputImage.fromBitmap(bitmap, 0)) }.getOrNull()?.let(::extractOrNull)

  private suspend fun recognize(image: InputImage): String = suspendCancellableCoroutine { continuation ->
    recognizer.process(image)
      .addOnSuccessListener { text -> if (continuation.isActive) continuation.resume(text.text) }
      .addOnFailureListener { if (continuation.isActive) continuation.resume("") }
  }

  private fun extractOrNull(rawText: String): ParsedReceipt? =
    rawText.takeIf { it.isNotBlank() }?.let(::extractReceiptDetails)

  private val upiMarkers = listOf(
    "upi", "utr", "transaction id", "txn id", "paid to", "received from", "paid you", "payment successful",
    "transaction successful", "completed", "google pay", "phonepe", "paytm", "bhim", "credited", "debited"
  )

  fun extractReceiptDetails(rawText: String): ParsedReceipt {
    val lower = rawText.lowercase()
    if (upiMarkers.any { lower.contains(it) } || Regex("""[a-z0-9.\-_]{2,}@[a-z]{2,}""").containsMatchIn(lower)) {
      PaymentMessageParser.parseScreenshot(rawText)?.let { p ->
        return ParsedReceipt(
          amount = p.amount,
          vendor = p.counterparty,
          dateString = null,
          invoiceNo = p.reference,
          suggestedCategory = if (p.type == "INCOME") "Sales" else "Other",
          rawText = rawText,
          confidenceScore = if (p.reference != null) 0.95f else 0.8f,
          type = p.type,
          referenceNumber = p.reference,
          fromParty = p.fromParty,
          toParty = p.toParty,
          vpas = p.vpas
        )
      }
    }
    return extractShopReceipt(rawText)
  }

  private fun extractShopReceipt(rawText: String): ParsedReceipt {
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

    // 1. Extract Vendor Name (usually in the first 3 lines)
    var vendor: String? = null
    for (i in 0 until minOf(4, lines.size)) {
      val line = lines[i]
      if (line.length in 3..40 &&
        !line.contains("receipt", ignoreCase = true) &&
        !line.contains("tax invoice", ignoreCase = true) &&
        !line.contains("cash memo", ignoreCase = true) &&
        !line.contains("welcome", ignoreCase = true)
      ) {
        vendor = line
        break
      }
    }

    // 2. Extract Amount
    var amount: Double? = null
    // Match "Total", "Grand Total", "Net Amount", "Amount Due", "Paid" followed by numbers
    val totalPattern = Pattern.compile(
      "(?:total|grand total|net amount|amount paid|subtotal|due|rs\\.?|₹)\\s*[:=-]?\\s*₹?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)",
      Pattern.CASE_INSENSITIVE
    )

    for (line in lines) {
      val matcher = totalPattern.matcher(line)
      if (matcher.find()) {
        val numStr = matcher.group(1)?.replace(",", "")
        val parsed = numStr?.toDoubleOrNull()
        if (parsed != null && (amount == null || parsed > amount)) {
          amount = parsed
        }
      }
    }

    // Fallback amount: largest numeric value found in lines ending with currency format
    if (amount == null) {
      val amountFallbackPattern = Pattern.compile("₹?\\s*([0-9]+(?:\\.[0-9]{2}))")
      val candidates = mutableListOf<Double>()
      for (line in lines) {
        val m = amountFallbackPattern.matcher(line)
        while (m.find()) {
          m.group(1)?.toDoubleOrNull()?.let { candidates.add(it) }
        }
      }
      if (candidates.isNotEmpty()) {
        amount = candidates.maxOrNull()
      }
    }

    // 3. Extract Date
    val datePattern = Pattern.compile(
      "([0-3]?[0-9][/-][0-1]?[0-9][/-](?:20)?[0-9]{2}|[0-3]?[0-9]\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(?:20)?[0-9]{2})",
      Pattern.CASE_INSENSITIVE
    )
    var dateStr: String? = null
    for (line in lines) {
      val m = datePattern.matcher(line)
      if (m.find()) {
        dateStr = m.group(1)
        break
      }
    }

    // 4. Extract Invoice/Bill No
    val invPattern = Pattern.compile(
      "(?:inv(?:oice)?(?:[\\s#.:-]*no\\.?)?|bill(?:[\\s#.:-]*no\\.?)?|receipt|txn|ref)[\\s#.:-]*([A-Za-z0-9/-]{3,20})",
      Pattern.CASE_INSENSITIVE
    )
    var invoiceNo: String? = null
    for (line in lines) {
      val m = invPattern.matcher(line)
      if (m.find()) {
        invoiceNo = m.group(1)
        break
      }
    }

    // 5. Categorization inference
    val lowerText = rawText.lowercase()
    val category = when {
      lowerText.contains("food") || lowerText.contains("cafe") || lowerText.contains("restaurant") || lowerText.contains("pizza") || lowerText.contains("dining") -> "Food"
      lowerText.contains("petrol") || lowerText.contains("fuel") || lowerText.contains("uber") || lowerText.contains("travel") || lowerText.contains("flight") -> "Travel"
      lowerText.contains("electric") || lowerText.contains("broadband") || lowerText.contains("telecom") || lowerText.contains("water") || lowerText.contains("internet") -> "Utilities"
      lowerText.contains("rent") || lowerText.contains("lease") || lowerText.contains("property") -> "Rent"
      lowerText.contains("cloud") || lowerText.contains("software") || lowerText.contains("hosting") || lowerText.contains("consulting") -> "Services"
      lowerText.contains("hardware") || lowerText.contains("procurement") || lowerText.contains("material") || lowerText.contains("stock") -> "Inventory"
      else -> "Supplies"
    }

    return ParsedReceipt(
      amount = amount,
      vendor = vendor,
      dateString = dateStr,
      invoiceNo = invoiceNo,
      suggestedCategory = category,
      rawText = rawText,
      confidenceScore = if (amount != null && vendor != null) 0.95f else 0.70f
    )
  }
}
