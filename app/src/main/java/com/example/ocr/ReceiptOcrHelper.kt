package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParsedReceipt(
  val amount: Double?,
  val vendor: String?,
  val type: String = "EXPENSE",
  val dateString: String?,
  val invoiceNo: String?, // UTR / Reference ID / Tracking ID / Bill No
  val upiId: String? = null, // VPA e.g. store@okhdfcbank
  val suggestedCategory: String,
  val rawText: String,
  val confidenceScore: Float = 0.9f,
  val extractionError: String? = null
)

object ReceiptOcrHelper {

  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  suspend fun parseReceiptImage(bitmap: Bitmap): ParsedReceipt {
    return try {
      val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
      } else {
        bitmap
      }
      val image = InputImage.fromBitmap(softwareBitmap, 0)
      val visionText = suspendCancellableCoroutine<String> { continuation ->
        recognizer.process(image)
          .addOnSuccessListener { text ->
            if (continuation.isActive) continuation.resume(text.text)
          }
          .addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
          }
      }
      extractReceiptDetails(visionText)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      extractReceiptDetails("").copy(extractionError = "Could not scan this image. Try a clearer image or enter the details manually.")
    }
  }

  suspend fun parseReceiptUri(context: Context, uri: Uri): ParsedReceipt {
    return try {
      val image = InputImage.fromFilePath(context, uri)
      val visionText = suspendCancellableCoroutine<String> { continuation ->
        recognizer.process(image)
          .addOnSuccessListener { text ->
            if (continuation.isActive) continuation.resume(text.text)
          }
          .addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
          }
      }
      extractReceiptDetails(visionText)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      extractReceiptDetails("").copy(extractionError = "Could not open or scan this image. Try another image or enter the details manually.")
    }
  }

  fun extractReceiptDetails(rawText: String): ParsedReceipt {
    if (rawText.isBlank()) {
      return ParsedReceipt(
        amount = null,
        vendor = null,
        type = "EXPENSE",
        dateString = null,
        invoiceNo = null,
        upiId = null,
        suggestedCategory = "Other",
        rawText = "",
        confidenceScore = 0.0f,
        extractionError = "No readable text found. Try a clearer image or enter the details manually."
      )
    }

    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val lowerText = rawText.lowercase()

    // 1. Transaction Type (Income vs Expense)
    val isIncome = Regex("\\b(received from|payment received|money received|amount received|credited to|credited into)\\b|\\breceived\\s+(?:₹|rs\\.?|inr)\\s*[0-9]", RegexOption.IGNORE_CASE)
      .containsMatchIn(rawText) || Regex("\\b(?:₹|rs\\.?|inr)\\s*[0-9,.]+\\s+(?:received|credited)\\b", RegexOption.IGNORE_CASE).containsMatchIn(rawText)
    val txnType = if (isIncome) "INCOME" else "EXPENSE"

    // 2. Extract UPI VPA / ID (e.g. paytmqr1rcvn1deli@paytm, user@okhdfcbank)
    val upiVpaPattern = Pattern.compile(
      "[a-zA-Z0-9.\\-_]{2,256}@(okhdfcbank|okicici|okaxis|oksbi|ybl|icici|paytm|axl|ibl|barodampay|mahb|postbank|upi)",
      Pattern.CASE_INSENSITIVE
    )
    var extractedUpiId: String? = null
    val upiMatcher = upiVpaPattern.matcher(rawText)
    if (upiMatcher.find()) {
      extractedUpiId = upiMatcher.group(0)
    }

    // 3. Extract UTR / Reference ID / Transaction ID / Tracking ID
    // OCR can split a label and its value across lines (as in PhonePe receipts),
    // so normalize common punctuation/whitespace before applying the patterns.
    val normalizedText = rawText
      .replace('\u00A0', ' ')
      .replace(Regex("[|]"), "I")
      .replace(Regex("[ \\t]+"), " ")
    var referenceId: String? = null

    // Check UTR explicit pattern e.g. "UTR: 951886448560" or "UTR 951886448560"
    val utrPattern = Pattern.compile("(?:utr|ref\\s*no|upi\\s*ref)\\s*[:#-]?\\s*([A-Za-z0-9]{8,22})", Pattern.CASE_INSENSITIVE)
    val normalizedUtrMatcher = utrPattern.matcher(normalizedText)
    if (normalizedUtrMatcher.find()) {
      referenceId = normalizedUtrMatcher.group(1)
    }
    if (referenceId == null) {
      for (line in lines) {
        val m = utrPattern.matcher(line)
        if (m.find()) {
          referenceId = m.group(1)
          break
        }
      }
    }

    // Check Transaction ID / PhonePe / Google Pay / Paytm Txn ID
    if (referenceId == null) {
      val refPattern = Pattern.compile(
        "(?:txn\\s*id|transaction\\s*id|ref\\s*(?:no\\.?)?|tracking\\s*id|inv(?:oice)?\\s*(?:no\\.?)?|bill\\s*(?:no\\.?)?)\\s*[:#-]?\\s*([A-Za-z0-9/-]{6,30})",
        Pattern.CASE_INSENSITIVE
      )
      for (i in lines.indices) {
        val line = lines[i]
        val m = refPattern.matcher(line)
        if (m.find()) {
          val candidate = m.group(1)?.trim()
          if (!candidate.isNullOrBlank() && candidate.length >= 4) {
            referenceId = candidate
            break
          }
        }
        if (line.contains("Transaction ID", ignoreCase = true) || line.contains("Txn ID", ignoreCase = true)) {
          if (i + 1 < lines.size) {
            val nextLine = lines[i + 1]
            if (nextLine.matches(Regex("[A-Za-z0-9/-]{8,35}"))) {
              referenceId = nextLine
              break
            }
          }
        }
      }
    }

    // Fallback UTR: Match standalone 12-digit numeric UPI reference number
    if (referenceId == null) {
      val utr12DigitPattern = Pattern.compile("\\b([0-9]{12})\\b")
      for (line in lines) {
        val m = utr12DigitPattern.matcher(line)
        if (m.find()) {
          referenceId = m.group(1)
          break
        }
      }
    }

    // 4. Pick the payable/paid total, not the largest number on the page.
    // A payment screenshot commonly also contains a balance, a masked account
    // number, and the same amount again beside "Debited from".
    val number = "((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,3}(?:,[0-9]{2})+,[0-9]{3}|[0-9]+)(?:\\.[0-9]{1,2})?)(?![0-9,.])"
    val money = "(?:₹|rs\\.?|inr)\\s*$number"
    val labeledAmount = Regex(
      "\\b(grand\\s+total|total\\s+(?:paid|amount)|amount\\s+(?:paid|received|sent)|payment\\s+(?:of|amount)|paid|received|total|amount)\\b\\s*[:=-]?\\s*(?:₹|rs\\.?|inr)?\\s*$number",
      RegexOption.IGNORE_CASE
    )
    val currencyAmount = Regex(money, RegexOption.IGNORE_CASE)
    val excludedAmountLine = Regex("\\b(balance|available|remaining|debited from|credited to|account|a/c|cashback|discount|subtotal|sub-total|tax|gst|fee|charge|saving|limit|due|refund)\\b", RegexOption.IGNORE_CASE)
    val candidates = mutableListOf<Pair<Int, Double>>()
    for ((index, line) in lines.withIndex()) {
      if (excludedAmountLine.containsMatchIn(line)) continue
      val label = labeledAmount.find(line)
      val currency = currencyAmount.find(line)
      // OCR often places the total label and the digits in separate blocks.
      val nextLineAmount = if (Regex("^(?:grand\\s+total|total(?:\\s+(?:paid|amount))?|amount(?:\\s+(?:paid|received|sent))?)\\s*[:=-]?\\s*$", RegexOption.IGNORE_CASE).matches(line)) {
        lines.getOrNull(index + 1)?.let { next ->
          Regex("^(?:₹|rs\\.?|inr)?\\s*$number\\s*$", RegexOption.IGNORE_CASE).matchEntire(next)?.groupValues?.get(1)
        }
      } else null
      val number = label?.groupValues?.get(2) ?: currency?.groupValues?.get(1) ?: nextLineAmount
      val value = number?.replace(",", "")?.toDoubleOrNull()
      if (value == null || !value.isFinite() || value <= 0.0) continue
      val text = line.lowercase()
      val priority = when {
        Regex("\\b(grand\\s+total|total\\s+paid|total\\s+amount|amount\\s+paid|amount\\s+received|amount\\s+sent)\\b").containsMatchIn(text) -> 5
        Regex("\\b(total|payment\\s+(?:of|amount)|paid|received)\\b").containsMatchIn(text) -> 4
        Regex("\\bamount\\b").containsMatchIn(text) -> 3
        currency != null && index < 7 -> 2
        currency != null -> 1
        else -> 0
      }
      if (priority > 0) candidates += priority to value
    }
    val amount = candidates.maxByOrNull { it.first }?.second

    // 5. Extract Payee / Payer / Vendor Name
    var vendorName: String? = null

    for (i in lines.indices) {
      val line = lines[i]
      val lowerLine = line.lowercase()

      if (lowerLine == "paid to" || lowerLine == "to" || lowerLine == "received from" || lowerLine == "from" || lowerLine == "transfer to") {
        if (i + 1 < lines.size) {
          val nextLine = lines[i + 1]
          if (nextLine.length in 2..40 &&
            !nextLine.contains("@") &&
            !nextLine.contains("₹") &&
            !nextLine.contains("Rs") &&
            !nextLine.contains("Transfer", ignoreCase = true)
          ) {
            vendorName = nextLine
            break
          }
        }
      }
    }

    if (vendorName == null) {
      val payeePayerPattern = Pattern.compile(
        "\\b(?:paid to|transfer to|sent to|received from|paid by|merchant|to|from)\\b\\s*[:=-]?\\s*([A-Za-z0-9\\s&.-]{3,35})",
        Pattern.CASE_INSENSITIVE
      )

      for (line in lines) {
        val m = payeePayerPattern.matcher(line)
        if (m.find()) {
          val candidate = m.group(1)?.trim()
          if (!candidate.isNullOrBlank() &&
            !candidate.contains("Total", ignoreCase = true) &&
            !candidate.contains("Invoice", ignoreCase = true) &&
            !candidate.contains("Amount", ignoreCase = true) &&
            !candidate.contains("Rs", ignoreCase = true) &&
            !candidate.equals("UPI", ignoreCase = true) &&
            !candidate.equals("Bank", ignoreCase = true) &&
            !candidate.equals("Successful", ignoreCase = true)
          ) {
            vendorName = candidate
            break
          }
        }
      }
    }

    if (vendorName == null) {
      for (i in 0 until minOf(4, lines.size)) {
        val line = lines[i]
        if (line.length in 3..40 &&
          !line.contains("payment", ignoreCase = true) &&
          !line.contains("successful", ignoreCase = true) &&
          !line.contains("transaction", ignoreCase = true) &&
          !line.contains("paid", ignoreCase = true) &&
          !line.contains("received", ignoreCase = true) &&
          !line.contains("receipt", ignoreCase = true) &&
          !line.contains("upi", ignoreCase = true)
        ) {
          vendorName = line
          break
        }
      }
    }

    // 6. Extract Date (e.g. 14 Aug 2026 or 05:54 pm on 14 Aug 2026)
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

    // 7. Category inference
    val category = when {
      lowerText.contains("tyre") || lowerText.contains("tyres") || lowerText.contains("auto") || lowerText.contains("motor") || lowerText.contains("vehicle") -> "Supplies"
      lowerText.contains("food") || lowerText.contains("cafe") || lowerText.contains("restaurant") || lowerText.contains("pizza") || lowerText.contains("dining") || lowerText.contains("swiggy") || lowerText.contains("zomato") -> "Food"
      lowerText.contains("petrol") || lowerText.contains("fuel") || lowerText.contains("uber") || lowerText.contains("ola") || lowerText.contains("travel") -> "Travel"
      lowerText.contains("electric") || lowerText.contains("broadband") || lowerText.contains("telecom") || lowerText.contains("recharge") || lowerText.contains("water") -> "Utilities"
      lowerText.contains("rent") || lowerText.contains("lease") || lowerText.contains("property") -> "Rent"
      lowerText.contains("sales") || lowerText.contains("consulting") || lowerText.contains("service") || lowerText.contains("fee") -> "Services"
      lowerText.contains("hardware") || lowerText.contains("procurement") || lowerText.contains("material") || lowerText.contains("inventory") -> "Inventory"
      else -> "Sales"
    }

    val score = when {
      amount != null && referenceId != null && vendorName != null -> 0.98f
      amount != null && (referenceId != null || vendorName != null) -> 0.90f
      amount != null -> 0.75f
      else -> 0.35f
    }

    return ParsedReceipt(
      amount = amount,
      vendor = vendorName,
      type = txnType,
      dateString = dateStr,
      invoiceNo = referenceId,
      upiId = extractedUpiId,
      suggestedCategory = category,
      rawText = rawText,
      confidenceScore = score,
      extractionError = if (amount == null) "No payment amount found. Check the image or enter the amount manually." else null
    )
  }
}
