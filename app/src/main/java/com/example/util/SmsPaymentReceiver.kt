package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.example.data.model.NotificationDraft
import java.security.MessageDigest

object SmsPaymentParser {
  private val rejected = Regex("\\b(otp|one.time password|verification code|login code|offer|cashback|reward|pre.?approved|apply now|failed|declined|reversed|refunded|unsuccessful|not successful|pending|scheduled|request to pay)\\b", RegexOption.IGNORE_CASE)
  private val income = Regex("\\b(credited|credit|received|recvd|deposited)\\b", RegexOption.IGNORE_CASE)
  private val expense = Regex("\\b(debited|debit|paid|sent|spent|transferred|withdrawn)\\b", RegexOption.IGNORE_CASE)
  private val money = Regex("(?:₹|\\b(?:rs\\.?|inr|amount\\s*[:=-]?)\\s*)\\s*([0-9]+(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
  private val reference = Regex("\\b(?:utr|ref(?:erence)?(?:\\s*(?:no|number|id))?|txn(?:\\s*(?:no|id))?|transaction\\s*id|upi\\s*(?:ref|id))\\s*[:#./-]?\\s*([A-Za-z0-9]{8,24})\\b", RegexOption.IGNORE_CASE)

  fun parseSmsText(sender: String?, body: String, timestamp: Long = System.currentTimeMillis()): NotificationDraft? {
    if (body.isBlank() || rejected.containsMatchIn(body)) return null
    val incoming = income.find(body)
    val outgoing = expense.find(body)
    // An ambiguous alert (e.g. a transfer between own accounts) needs manual entry.
    if ((incoming == null) == (outgoing == null)) return null
    val direction = incoming ?: outgoing!!
    val matches = money.findAll(body).filter { match ->
      val prefix = body.substring(maxOf(0, match.range.first - 32), match.range.first)
      !Regex("(?:avail(?:able)?|closing|remaining|current|ledger|opening)\\s*(?:bal(?:ance)?|amount)?\\s*[:=-]?\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix) &&
        !Regex("\\b(?:bal(?:ance)?|limit)\\s*[:=-]?\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix)
    }.toList()
    val amountMatch = matches.minByOrNull { kotlin.math.abs(it.range.first - direction.range.first) } ?: return null
    val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull()
    if (amount == null || !amount.isFinite() || amount <= 0.0) return null
    val ref = reference.find(body)?.groupValues?.get(1)
    val normalizedSender = sender?.trim().orEmpty().ifBlank { "Bank SMS" }
    val idInput = if (ref != null) "${ref.lowercase()}|${if (incoming != null) "INCOME" else "EXPENSE"}" else
      "${normalizedSender.lowercase()}|${body.trim().lowercase()}|${timestamp / 300000L}"
    val digest = MessageDigest.getInstance("SHA-256").digest(idInput.toByteArray())
      .joinToString("") { "%02x".format(it) }
    return NotificationDraft(
      id = "sms_$digest", senderApp = "SMS ($normalizedSender)", amount = amount,
      type = if (incoming != null) "INCOME" else "EXPENSE", rawText = body,
      senderOrReceiver = normalizedSender, timestamp = timestamp, upiReference = ref
    )
  }

  fun readRealSmsInboxPayments(context: Context): List<NotificationDraft> {
    val drafts = mutableListOf<NotificationDraft>()
    val cursor = context.contentResolver.query(
      Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"),
      null, null, "date DESC"
    ) ?: throw IllegalStateException("SMS inbox unavailable")
    cursor.use { c ->
      val address = c.getColumnIndexOrThrow("address")
      val body = c.getColumnIndexOrThrow("body")
      val date = c.getColumnIndexOrThrow("date")
      while (c.moveToNext()) {
        parseSmsText(c.getString(address), c.getString(body).orEmpty(), c.getLong(date))?.let(drafts::add)
      }
    }
    return drafts.distinctBy { it.id }
  }
}

class SmsPaymentReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
    try {
      val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
      // Multipart messages share one payment: parse the complete body once per sender.
      messages.groupBy { it.displayOriginatingAddress }.forEach { (sender, parts) ->
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val draft = SmsPaymentParser.parseSmsText(sender, body, timestamp) ?: return@forEach
        if (SmsDraftStore(context).add(draft)) {
          context.sendBroadcast(Intent(ACTION_PAYMENT_SMS_RECEIVED).setPackage(context.packageName))
        }
      }
    } catch (e: Exception) {
      Log.e("SmsPaymentReceiver", "Failed to process SMS", e)
    }
  }

  companion object { const val ACTION_PAYMENT_SMS_RECEIVED = "com.example.ACTION_PAYMENT_SMS_RECEIVED" }
}
