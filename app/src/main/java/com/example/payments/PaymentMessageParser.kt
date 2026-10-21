package com.example.payments

import java.util.Locale

/** A money movement read from a bank SMS, a UPI app notification or a payment screenshot. */
data class ParsedPayment(
  val amount: Double,
  val type: String, // "INCOME" or "EXPENSE"
  val counterparty: String?,
  val reference: String?,
  /** The payer and payee as printed on a screenshot ("From: …", "To: …"), when present. */
  val fromParty: String? = null,
  val toParty: String? = null,
  val vpas: List<String> = emptyList()
)

/**
 * Reads Indian bank SMS, UPI app notifications (GPay, PhonePe, Paytm, BHIM, business apps) and OCR text of
 * payment screenshots. Returns null for anything that is not a completed payment (OTPs, collect requests,
 * reminders, failed payments, promotions).
 */
object PaymentMessageParser {

  private val amountRegex = Regex(
    """(?:₹|rs\.?|inr|rupees)\s*:?\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
    RegexOption.IGNORE_CASE
  )

  private val referenceRegex = Regex(
    """(?:upi\s*(?:ref(?:erence)?|txn|transaction)?\s*(?:no|id|number)?|utr(?:\s*no)?|rrn|ref(?:erence)?\s*(?:no|id|number)?|txn\s*(?:id|no)|transaction\s*(?:id|no|number))\.?\s*[:#\-]?\s*([0-9]{10,18})""",
    RegexOption.IGNORE_CASE
  )
  private val bareUtrRegex = Regex("""(?<![0-9])([0-9]{12})(?![0-9])""")
  private val vpaRegex = Regex("""[a-zA-Z0-9.\-_]{2,64}@[a-zA-Z]{2,64}""")

  private val incomeKeywords = listOf(
    "credited", "received", "paid you", "sent you", "deposited", "added to your", "money in"
  )
  private val expenseKeywords = listOf(
    "debited", "paid to", "sent to", "you paid", "payment to", "withdrawn", "spent", "transferred to", "paid successfully"
  )
  private val rejectKeywords = listOf(
    "otp", "one time password", "requested", "request money", "collect request", "is requesting", "will be debited",
    "due on", "due date", "reminder", "failed", "declined", "unsuccessful", "reversed", "cashback upto", "offer", "win ",
    "loan", "emi of", "auto-pay scheduled", "mandate", "pending"
  )

  /** Parses an SMS or notification. Needs both an amount and a clear credit/debit verb. */
  fun parseMessage(text: String): ParsedPayment? {
    val flat = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    val lower = flat.lowercase(Locale.ROOT)
    if (rejectKeywords.any { lower.contains(it) }) return null

    val type = detectType(lower) ?: return null
    val amount = firstAmount(flat) ?: return null
    return ParsedPayment(
      amount = amount,
      type = type,
      // Line by line first, so a name doesn't run into the next line of a notification.
      counterparty = text.lines().firstNotNullOfOrNull { counterparty(it.trim(), type) } ?: counterparty(flat, type),
      reference = reference(flat),
      vpas = vpaRegex.findAll(flat).map { it.value }.toList()
    )
  }

  /**
   * Parses OCR text of a payment screenshot (often photographed from the customer's phone). Screenshots show the
   * amount large on its own line, sometimes with the ₹ sign misread, so this is more lenient than [parseMessage].
   */
  fun parseScreenshot(rawText: String): ParsedPayment? {
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    val flat = lines.joinToString(" ")
    val lower = flat.lowercase(Locale.ROOT)

    val amount = firstAmount(flat) ?: standaloneAmount(lines) ?: return null
    val fromParty = labelledParty(lines, listOf("from", "received from", "paid by", "sender"))
    val toParty = labelledParty(lines, listOf("to", "paid to", "sent to", "receiver", "beneficiary"))
    val type = detectType(lower) ?: "EXPENSE"
    return ParsedPayment(
      amount = amount,
      type = type,
      counterparty = (if (type == "INCOME") fromParty else toParty) ?: counterparty(flat, type),
      reference = reference(flat),
      fromParty = fromParty,
      toParty = toParty,
      vpas = vpaRegex.findAll(flat).map { it.value }.distinct().toList()
    )
  }

  private fun detectType(lower: String): String? {
    // Earliest keyword wins: "credited to your a/c … debited from payer" should be a credit.
    val income = incomeKeywords.mapNotNull { k -> lower.indexOf(k).takeIf { it >= 0 } }.minOrNull()
    val expense = expenseKeywords.mapNotNull { k -> lower.indexOf(k).takeIf { it >= 0 } }.minOrNull()
    return when {
      income == null && expense == null -> null
      expense == null -> "INCOME"
      income == null -> "EXPENSE"
      income <= expense -> "INCOME"
      else -> "EXPENSE"
    }
  }

  // SBI writes "debited by 500.0" without a currency sign.
  private val verbAmountRegex = Regex(
    """(?:debited|credited)\s+(?:by|for|with)\s+([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)""",
    RegexOption.IGNORE_CASE
  )

  private fun firstAmount(text: String): Double? =
    (amountRegex.findAll(text) + verbAmountRegex.findAll(text))
      .mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.firstOrNull { it > 0 }

  /** A line that is only an amount, e.g. "₹500", "500.00", or "?500"/"R500"/"%500" when OCR misreads the ₹ sign. */
  private fun standaloneAmount(lines: List<String>): Double? {
    val lineRegex = Regex("""^[₹?%R*Z]?\s*([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]{1,7}(?:\.[0-9]{1,2})?)\s*$""")
    return lines.asSequence()
      .mapNotNull { lineRegex.find(it)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() }
      .firstOrNull { it > 0 }
  }

  private fun reference(text: String): String? =
    referenceRegex.find(text)?.groupValues?.get(1) ?: bareUtrRegex.find(text)?.groupValues?.get(1)

  /** "From: Rahul Sharma" or a "From" line followed by the name on the next line. */
  private fun labelledParty(lines: List<String>, labels: List<String>): String? {
    for ((i, line) in lines.withIndex()) {
      val lower = line.lowercase(Locale.ROOT)
      for (label in labels.sortedByDescending { it.length }) {
        if (lower == label || lower == "$label:") {
          return lines.getOrNull(i + 1)?.let(::cleanName)
        }
        if (lower.startsWith("$label:") || lower.startsWith("$label ")) {
          val rest = line.substring(label.length).trimStart(':', ' ')
          if (rest.isNotBlank()) return cleanName(rest)
        }
      }
    }
    return null
  }

  private fun counterparty(text: String, type: String): String? {
    val stop = """(?=\s+(?:via|on|ref|refno|upi|using|in|for|at|with|towards|a/c|account|is|has|was)\b|[,;(]|\.(?=\s|$)|\s*$)"""
    val patterns = if (type == "INCOME") listOf(
      // "from" names the payer more reliably than "by" ("credited by UPI from Sunita").
      Regex("""\bfrom\s+(?:vpa\s+)?(.+?)$stop""", RegexOption.IGNORE_CASE),
      Regex("""\bby\s+(?!upi\b|neft\b|imps\b|rtgs\b)(?:vpa\s+)?(.+?)$stop""", RegexOption.IGNORE_CASE),
      Regex("""^(.+?)\s+(?:has\s+)?(?:paid|sent)\s+you""", RegexOption.IGNORE_CASE)
    ) else listOf(
      Regex("""(?:paid|sent|transferred|payment)\s+to\s+(?:vpa\s+)?(.+?)$stop""", RegexOption.IGNORE_CASE),
      Regex("""\bto\s+(?:vpa\s+)?(.+?)$stop""", RegexOption.IGNORE_CASE),
      Regex("""\bat\s+(.+?)$stop""", RegexOption.IGNORE_CASE)
    )
    for (p in patterns) {
      val name = p.find(text)?.groupValues?.get(1)?.let(::cleanName)
      if (!name.isNullOrBlank() && !name.lowercase(Locale.ROOT).startsWith("your")) return name
    }
    return null
  }

  private fun cleanName(raw: String): String? {
    val name = raw.replace(amountRegex, "").trim().trim(':', '-', '.', ',', ' ')
    return name.takeIf { it.length in 2..60 && it.any(Char::isLetter) }
  }
}
