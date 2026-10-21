package com.example.payments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Reads bank credit/debit SMS the moment they arrive (needs the RECEIVE_SMS permission). */
class PaymentSmsReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
    val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
    // A long SMS arrives in several parts; join them per sender.
    parts.filterNotNull().groupBy { it.originatingAddress.orEmpty() }.forEach { (sender, msgs) ->
      val body = msgs.joinToString("") { it.messageBody.orEmpty() }
      val at = msgs.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
      PaymentInbox.record(context, body, PaymentInbox.smsSourceLabel(sender), at)
    }
  }
}
