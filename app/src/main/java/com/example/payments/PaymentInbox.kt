package com.example.payments

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.model.NotificationDraft
import com.example.ui.components.formatCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Turns incoming payment messages (bank SMS, UPI app notifications) into drafts in the Pay Inbox.
 * The same payment usually arrives twice (bank SMS + app notification), so drafts are de-duplicated by UPI
 * reference, or by amount and type within a few minutes, against both drafts and posted transactions.
 */
object PaymentInbox {
  private const val CHANNEL_ID = "payments_received"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Mutex()

  /** Known UPI and payment apps, consumer and merchant ("for Business") editions. */
  val paymentApps = mapOf(
    "com.google.android.apps.nbu.paisa.user" to "Google Pay",
    "com.google.android.apps.nbu.paisa.merchant" to "Google Pay Business",
    "com.phonepe.app" to "PhonePe",
    "com.phonepe.app.business" to "PhonePe Business",
    "net.one97.paytm" to "Paytm",
    "com.paytm.business" to "Paytm Business",
    "in.org.npci.upiapp" to "BHIM",
    "com.bharatpe.app" to "BharatPe",
    "com.bharatpe.merchant" to "BharatPe",
    "in.amazon.mShop.android.shopping" to "Amazon Pay",
    "com.dreamplug.androidapp" to "CRED",
    "com.mobikwik_new" to "MobiKwik",
    "com.freecharge.android" to "Freecharge",
    "com.whatsapp" to "WhatsApp Pay",
    "com.sbi.upi" to "BHIM SBI Pay",
    "com.csam.icici.bank.imobile" to "iMobile",
    "com.snapwork.hdfc" to "HDFC Bank",
    "com.axis.mobile" to "Axis Mobile",
    "com.msf.kbank.mobile" to "Kotak",
    "com.sbi.lotusintouch" to "YONO SBI",
  )

  /** SMS apps: their notifications carry bank SMS when this app can't read SMS directly. */
  private val smsApps = setOf(
    "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
    "com.android.messaging", "com.oneplus.mms", "com.miui.sms", "com.truecaller"
  )

  fun isWatchedApp(pkg: String): Boolean = pkg in paymentApps || pkg in smsApps

  fun record(context: Context, text: String, source: String, timestamp: Long = System.currentTimeMillis()) {
    val parsed = PaymentMessageParser.parseMessage(text) ?: return
    val app = context.applicationContext
    scope.launch { insertIfNew(app, parsed, text, source, timestamp, notify = true) }
  }

  /** Reads bank SMS from the last [days] days so payments that arrived before the app was opened are not missed. */
  suspend fun scanSmsInbox(context: Context, days: Int = 3): Int {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return 0
    val since = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
    var added = 0
    val rows = runCatching {
      context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
        "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC"
      )
    }.getOrNull() ?: return 0
    val messages = mutableListOf<Triple<String, String, Long>>()
    rows.use { c ->
      while (c.moveToNext()) messages += Triple(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2))
    }
    for ((sender, body, date) in messages) {
      val parsed = PaymentMessageParser.parseMessage(body) ?: continue
      if (insertIfNew(context.applicationContext, parsed, body, smsSourceLabel(sender), date, notify = false)) added++
    }
    return added
  }

  fun smsSourceLabel(sender: String): String {
    // Bank sender ids look like "VM-HDFCBK" / "AD-SBIUPI-S".
    val id = sender.substringAfter('-').substringBefore('-').uppercase()
    return when {
      id.startsWith("HDFC") -> "HDFC Bank SMS"
      id.startsWith("SBI") || id.startsWith("CBSSBI") -> "SBI SMS"
      id.startsWith("ICICI") -> "ICICI Bank SMS"
      id.startsWith("AXIS") -> "Axis Bank SMS"
      id.startsWith("KOTAK") -> "Kotak SMS"
      id.isNotBlank() && id.any(Char::isLetter) -> "$id SMS"
      else -> "Bank SMS"
    }
  }

  private suspend fun insertIfNew(
    context: Context, parsed: ParsedPayment, rawText: String, source: String, timestamp: Long, notify: Boolean
  ): Boolean = lock.withLock {
    val db = AppDatabase.getInstance(context)
    val window = 10 * 60 * 1000L
    val since = timestamp - window
    val drafts = db.paymentDraftDao().getDraftsSince(since)
    val txns = db.transactionDao().getTransactionsSince(since)
    val ref = parsed.reference
    val duplicate =
      (ref != null && (drafts.any { it.upiReference == ref } ||
        txns.any { it.referenceNumber == ref || it.note.contains(ref) })) ||
        drafts.any { sameMoney(it.amount, it.type, it.timestamp, parsed, timestamp) } ||
        txns.any { it.source != "MANUAL" && sameMoney(it.amount, it.type, it.timestamp, parsed, timestamp) }
    if (duplicate) return@withLock false

    val draft = NotificationDraft(
      id = ref?.let { "ref_$it" } ?: "msg_${timestamp}_${parsed.amount}",
      senderApp = source,
      amount = parsed.amount,
      type = parsed.type,
      rawText = rawText.take(500),
      senderOrReceiver = parsed.counterparty ?: if (parsed.type == "INCOME") "UPI payer" else "UPI payee",
      timestamp = timestamp,
      upiReference = ref
    )
    val inserted = db.paymentDraftDao().insertDraft(draft) != -1L
    if (inserted && notify) showNotification(context, draft)
    inserted
  }

  private fun sameMoney(amount: Double, type: String, at: Long, p: ParsedPayment, pAt: Long) =
    abs(amount - p.amount) < 0.01 && type == p.type && abs(at - pAt) < 3 * 60 * 1000L

  private fun showNotification(context: Context, draft: NotificationDraft) {
    if (Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val nm = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL_ID) == null) {
      nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Payments received", NotificationManager.IMPORTANCE_HIGH))
    }
    val open = PendingIntent.getActivity(
      context, 0,
      Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PAY_INBOX)
        .setData(Uri.parse("upimanager://inbox"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val title = if (draft.type == "INCOME") "${formatCurrency(draft.amount)} received" else "${formatCurrency(draft.amount)} paid"
    val who = if (draft.type == "INCOME") "from ${draft.senderOrReceiver}" else "to ${draft.senderOrReceiver}"
    val n = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_more)
      .setContentTitle(title)
      .setContentText("$who • ${draft.senderApp}. Tap to post it to the ledger.")
      .setContentIntent(open)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .build()
    runCatching { NotificationManagerCompat.from(context).notify(draft.id.hashCode(), n) }
  }
}
