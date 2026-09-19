package com.example.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class ParsedReceipt(
  val amount: Double?,
  val vendor: String?,
  val type: String = "EXPENSE",
  val dateString: String?,
  val invoiceNo: String?, // UTR / Reference ID / Tracking ID / Bill No
  val upiId: String? = null, // VPA e.g. store@okhdfcbank
  val suggestedCategory: String,
  val rawText: String,
  val confidenceScore: Float = 0.9f
)

object ReceiptOcrHelper {

  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  suspend fun parseReceiptImage(bitmap: Bitmap): ParsedReceipt {
    return try {
      val image = InputImage.fromBitmap(bitmap, 0)
      val visionText = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
          .addOnSuccessListener { text ->
            if (continuation.isActive) continuation.resume(text.text)
          }
          .addOnFailureListener {
            if (continuation.isActive) continuation.resume("")
          }
      }
      extractReceiptDetails(visionText)
    } catch (e: Exception) {
      extractReceiptDetails("")
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
        confidenceScore = 0.0f
      )
    }

    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val lowerText = rawText.lowercase()

    // 1. Transaction Type (Income vs Expense)
    val isIncome = lowerText.contains("received from") ||
        lowerText.contains("received ₹") ||
        lowerText.contains("received rs") ||
        lowerText.contains("credited") ||
        lowerText.contains("credit") ||
        lowerText.contains("payment received") ||
        lowerText.contains("money received")
    val txnType = if (isIncome) "INCOME" else "EXPENSE"

    // 2. Extract UPI VPA / ID
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
    var referenceId: String? = null
    val refPattern = Pattern.compile(
      "(?:upi\\s*ref(?:erence)?\\s*(?:no\\.?)?|utr\\s*(?:no\\.?)?|txn\\s*id|transaction\\s*id|ref\\s*(?:no\\.?)?|tracking\\s*id|inv(?:oice)?\\s*(?:no\\.?)?|bill\\s*(?:no\\.?)?)\\s*[:#-]?\\s*([A-Za-z0-9/-]{6,30})",
      Pattern.CASE_INSENSITIVE
    )
    for (line in lines) {
      val m = refPattern.matcher(line)
      if (m.find()) {
        val candidate = m.group(1)?.trim()
        if (!candidate.isNullOrBlank() && candidate.length >= 3) {
          referenceId = candidate
          break
        }
      }
    }

    // Fallback UTR: Match 12-digit numeric UPI reference number
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

    // 4. Extract Amount
    var amount: Double? = null
    val amountPattern = Pattern.compile(
      "(?:total|grand total|amount|paid|received|due|rs\\.?|₹|inr)\\s*[:=-]?\\s*₹?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)",
      Pattern.CASE_INSENSITIVE
    )

    for (line in lines) {
      val matcher = amountPattern.matcher(line)
      if (matcher.find()) {
        val numStr = matcher.group(1)?.replace(",", "")
        val parsed = numStr?.toDoubleOrNull()
        if (parsed != null && parsed > 0.0 && (amount == null || parsed > amount)) {
          amount = parsed
        }
      }
    }

    if (amount == null) {
      val standaloneAmountPattern = Pattern.compile("₹\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)")
      for (line in lines) {
        val m = standaloneAmountPattern.matcher(line)
        if (m.find()) {
          val numStr = m.group(1)?.replace(",", "")
          val parsed = numStr?.toDoubleOrNull()
          if (parsed != null && parsed > 0.0) {
            amount = parsed
            break
          }
        }
      }
    }

    // 5. Extract Payee / Payer / Vendor Name
    var vendorName: String? = null
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

    if (vendorName == null) {
      for (i in 0 until minOf(4, lines.size)) {
        val line = lines[i]
        if (line.length in 3..40 &&
          !line.contains("payment", ignoreCase = true) &&
          !line.contains("successful", ignoreCase = true) &&
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

    // 6. Extract Date
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
      lowerText.contains("food") || lowerText.contains("cafe") || lowerText.contains("restaurant") || lowerText.contains("pizza") || lowerText.contains("dining") || lowerText.contains("swiggy") || lowerText.contains("zomato") -> "Food"
      lowerText.contains("petrol") || lowerText.contains("fuel") || lowerText.contains("uber") || lowerText.contains("ola") || lowerText.contains("travel") -> "Travel"
      lowerText.contains("electric") || lowerText.contains("broadband") || lowerText.contains("telecom") || lowerText.contains("recharge") || lowerText.contains("water") -> "Utilities"
      lowerText.contains("rent") || lowerText.contains("lease") || lowerText.contains("property") -> "Rent"
      lowerText.contains("sales") || lowerText.contains("consulting") || lowerText.contains("service") || lowerText.contains("fee") -> "Services"
      lowerText.contains("hardware") || lowerText.contains("procurement") || lowerText.contains("material") || lowerText.contains("inventory") -> "Inventory"
      else -> "Sales"
    }

    val score = if (amount != null && (referenceId != null || vendorName != null)) 0.95f else 0.70f

    return ParsedReceipt(
      amount = amount,
      vendor = vendorName,
      type = txnType,
      dateString = dateStr,
      invoiceNo = referenceId,
      upiId = extractedUpiId,
      suggestedCategory = category,
      rawText = rawText,
      confidenceScore = score
    )
  }
}
