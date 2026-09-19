package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.example.data.model.NotificationDraft
import java.util.regex.Pattern

object SmsPaymentParser {

  fun parseSmsText(sender: String?, body: String): NotificationDraft? {
    if (body.isBlank()) return null

    val lower = body.lowercase()

    // Filter out non-payment SMS
    val isPaymentSms = lower.contains("credited") ||
        lower.contains("debited") ||
        lower.contains("paid") ||
        lower.contains("received") ||
        lower.contains("upi") ||
        lower.contains("vpa") ||
        lower.contains("a/c") ||
        lower.contains("acct")

    if (!isPaymentSms) return null

    // Determine type (INCOME vs EXPENSE)
    val isIncome = lower.contains("credited") ||
        lower.contains("received") ||
        lower.contains("credit")

    val type = if (isIncome) "INCOME" else "EXPENSE"

    // Extract amount
    var amount: Double? = null
    val amountPattern = Pattern.compile(
      "(?:rs\\.?|₹|inr|amount)\\s*[:=-]?\\s*₹?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)",
      Pattern.CASE_INSENSITIVE
    )
    val matcher = amountPattern.matcher(body)
    if (matcher.find()) {
      amount = matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
    }

    if (amount == null || amount <= 0.0) return null

    // Extract UTR / Ref Number
    var reference: String? = null
    val refPattern = Pattern.compile(
      "(?:ref|utr|upi|txn)\\s*[:#.-]?\\s*([A-Za-z0-9]{8,20})",
      Pattern.CASE_INSENSITIVE
    )
    val refMatcher = refPattern.matcher(body)
    if (refMatcher.find()) {
      reference = refMatcher.group(1)
    }

    val senderName = sender ?: "Bank SMS"

    return NotificationDraft(
      id = "sms_${System.currentTimeMillis()}_${(100..999).random()}",
      senderApp = "SMS (${senderName})",
      amount = amount,
      type = type,
      rawText = body,
      senderOrReceiver = senderName,
      timestamp = System.currentTimeMillis(),
      upiReference = reference
    )
  }
}

class SmsPaymentReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context?, intent: Intent?) {
    if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || context == null) return

    try {
      val messages: Array<out SmsMessage>? = Telephony.Sms.Intents.getMessagesFromIntent(intent)
      if (messages.isNullOrEmpty()) return

      for (msg in messages) {
        val sender = msg.displayOriginatingAddress
        val body = msg.displayMessageBody ?: continue

        val draft = SmsPaymentParser.parseSmsText(sender, body)
        if (draft != null) {
          Log.d("SmsPaymentReceiver", "Parsed payment SMS: ${draft.amount} (${draft.type})")
          // Broadcast intent locally to MainActivity / AppViewModel
          val broadcastIntent = Intent("com.example.ACTION_PAYMENT_SMS_RECEIVED").apply {
            putExtra("EXTRA_DRAFT_ID", draft.id)
            putExtra("EXTRA_SENDER", draft.senderApp)
            putExtra("EXTRA_AMOUNT", draft.amount)
            putExtra("EXTRA_TYPE", draft.type)
            putExtra("EXTRA_RAW", draft.rawText)
            putExtra("EXTRA_PARTY", draft.senderOrReceiver)
            putExtra("EXTRA_REF", draft.upiReference)
            setPackage(context.packageName)
          }
          context.sendBroadcast(broadcastIntent)
        }
      }
    } catch (e: Exception) {
      Log.e("SmsPaymentReceiver", "Failed to parse SMS", e)
    }
  }
}
