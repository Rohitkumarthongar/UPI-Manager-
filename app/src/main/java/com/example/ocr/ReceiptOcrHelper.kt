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
  /** "INCOME" or "EXPENSE"; a shop receipt is always an expense. */
  val type: String = "EXPENSE",
  val isUpiScreenshot: Boolean = false,
  val referenceNumber: String? = null,
  val fromParty: String? = null,
  val toParty: String? = null,
  val vpas: List<String> = emptyList(),
  /** Why the scan couldn't be used, phrased for the user; null when an amount was found. */
  val extractionError: String? = null
) {
  val upiId: String? get() = vpas.firstOrNull()
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

  private const val NO_TEXT = "No text found. Try a clearer image, closer and in focus."
  private const val NO_AMOUNT = "Couldn't find the amount. Retake the photo or type the amount in."

  private val datePattern = Pattern.compile(
    "([0-3]?[0-9][/-][0-1]?[0-9][/-](?:20)?[0-9]{2}|[0-3]?[0-9]\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*,?\\s+(?:20)?[0-9]{2,4})",
    Pattern.CASE_INSENSITIVE
  )

  private fun findDate(lines: List<String>): String? =
    lines.firstNotNullOfOrNull { line -> datePattern.matcher(line).let { if (it.find()) it.group(1) else null } }

  fun extractReceiptDetails(rawText: String): ParsedReceipt {
    if (rawText.isBlank()) {
      return ParsedReceipt(null, null, null, null, "Other", rawText, 0f, extractionError = NO_TEXT)
    }
    val lower = rawText.lowercase()
    if (upiMarkers.any { lower.contains(it) } || Regex("""[a-z0-9.\-_]{2,}@[a-z]{2,}""").containsMatchIn(lower)) {
      PaymentMessageParser.parseScreenshot(rawText)?.let { p ->
        return ParsedReceipt(
          amount = p.amount,
          vendor = p.counterparty,
          dateString = findDate(rawText.lines()),
          invoiceNo = p.reference,
          suggestedCategory = if (p.type == "INCOME") "Sales" else "Other",
          rawText = rawText,
          confidenceScore = if (p.reference != null) 0.95f else 0.8f,
          type = p.type,
          isUpiScreenshot = true,
          referenceNumber = p.reference,
          fromParty = p.fromParty,
          toParty = p.toParty,
          vpas = p.vpas
        )
      }
    }
    return extractShopReceipt(rawText)
  }

  private val numberRegex = Regex("""(?:₹|rs\.?|inr)?\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
  private val ignoredLine = Regex("""balance|sub\s*total|discount|\bdue\b|saved|savings|change|\btax\b|gst|account|a/c""", RegexOption.IGNORE_CASE)
  private val totalLabel = Regex("""grand\s*total|net\s*(?:amount|payable)|amount\s*paid|total\s*paid|\bpaid\b|\btotal\b""", RegexOption.IGNORE_CASE)

  private fun numbersIn(line: String): List<Double> =
    numberRegex.findAll(line).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
      .filter { it > 0 && it < 10_000_000 }.toList()

  private fun shopAmount(lines: List<String>): Double? {
    // A total label, with its amount on the same line or (when OCR splits blocks) the next line. Last one wins:
    // a grand total comes after the item totals.
    var labelled: Double? = null
    for ((i, line) in lines.withIndex()) {
      if (ignoredLine.containsMatchIn(line) || !totalLabel.containsMatchIn(line)) continue
      val value = numbersIn(line).lastOrNull()
        ?: lines.getOrNull(i + 1)?.takeIf { !ignoredLine.containsMatchIn(it) }?.let { numbersIn(it).singleOrNull() }
      if (value != null) labelled = value
    }
    if (labelled != null) return labelled
    // Otherwise the largest currency-marked amount outside ignored lines.
    return lines.filterNot { ignoredLine.containsMatchIn(it) }
      .flatMap { line -> Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE).findAll(line).toList() }
      .mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
      .maxOrNull()
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

    // 2. Extract Amount: the paid total, never a subtotal, discount, amount due, tax or balance line.
    val amount = shopAmount(lines)

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
      confidenceScore = if (amount != null && vendor != null) 0.95f else 0.70f,
      extractionError = if (amount == null) NO_AMOUNT else null
    )
  }
}
