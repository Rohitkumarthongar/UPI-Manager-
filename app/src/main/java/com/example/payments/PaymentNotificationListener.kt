package com.example.payments

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads "₹500 received" notifications from UPI apps (GPay, PhonePe, Paytm, BHIM, business apps), bank apps and
 * SMS apps. The user turns it on once in Settings → Notification access.
 */
class PaymentNotificationListener : NotificationListenerService() {

  override fun onListenerConnected() {
    // Catch what arrived while the listener was off (e.g. right after it was enabled).
    runCatching { activeNotifications }.getOrNull()?.forEach(::handle)
  }

  override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

  private fun handle(sbn: StatusBarNotification) {
    if (sbn.packageName == packageName || !PaymentInbox.isWatchedApp(sbn.packageName)) return
    val extras = sbn.notification.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
      ?.toString().orEmpty()
    if (title.isBlank() && text.isBlank()) return
    val source = PaymentInbox.paymentApps[sbn.packageName] ?: "SMS • $title".take(40)
    // Body first: "Rahul paid you ₹500" names the payer better than the "₹500 received" title.
    PaymentInbox.record(this, "$text\n$title", source, sbn.postTime)
  }

  companion object {
    fun isEnabled(context: Context): Boolean {
      val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
      val me = ComponentName(context, PaymentNotificationListener::class.java)
      return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
  }
}
