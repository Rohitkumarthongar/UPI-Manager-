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
    var referenceId: String? = null

    // Check UTR explicit pattern e.g. "UTR: 951886448560" or "UTR 951886448560"
    val utrPattern = Pattern.compile("(?:utr|ref\\s*no|upi\\s*ref)\\s*[:#-]?\\s*([A-Za-z0-9]{8,22})", Pattern.CASE_INSENSITIVE)
    for (line in lines) {
      val m = utrPattern.matcher(line)
      if (m.find()) {
        referenceId = m.group(1)
        break
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
        // Handle next-line Transaction ID (e.g. Line 1: "PhonePe Transaction ID", Line 2: "T2608141754066026713528")
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

    // 4. Extract Amount (e.g. ₹22,000 or ₹22,000.00 or Rs. 22000)
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

    // Check multi-line "Paid to" or "Received from" or "To:" or "From:" (e.g. Line 1: "Paid to", Line 2: "Puniya tyres")
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

    // Inline payee/payer check
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

    // Header line fallback
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
