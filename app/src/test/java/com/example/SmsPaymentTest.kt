package com.example

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.util.SmsDraftStore
import com.example.util.SmsPaymentParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmsPaymentTest {
  @Test fun committedDecisionFiltersStalePendingData() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("sms_payment_drafts", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    val draft = SmsPaymentParser.parseSmsText("BANK", "INR 650 credited Ref 123456789012", 1700000000000L)!!
    SmsDraftStore(context).add(draft)
    prefs.edit().putStringSet("decided", setOf(draft.id)).commit()
    assertTrue(SmsDraftStore(context).pending().isEmpty())
  }

  @Test fun readsIncomeExpenseAndOriginalReference() {
    val income = SmsPaymentParser.parseSmsText("HDFC", "Your a/c credited INR 1,250.50 by UPI. Avl bal INR 20,000. Ref: 123456789012", 1230000L)!!
    assertEquals("INCOME", income.type)
    assertEquals(1250.50, income.amount, 0.001)
    assertEquals("123456789012", income.upiReference)
    assertEquals(1230000L, income.timestamp)
    val expense = SmsPaymentParser.parseSmsText("ICICI", "Rs. 450 debited from A/c XX9012. Balance Rs 1200. UTR 987654321012", 1240000L)!!
    assertEquals("EXPENSE", expense.type)
    assertEquals(450.0, expense.amount, 0.001)
    assertEquals(75.0, SmsPaymentParser.parseSmsText("SBI", "Amount: 75 received in account", 1250000L)!!.amount, 0.001)
  }

  @Test fun rejectsCodesPromotionsFailuresAndBalanceOnly() {
    listOf(
      "OTP 123456 for INR 500 paid via UPI",
      "Offer! You received Rs 500 cashback",
      "Transaction failed: Rs 500 debited",
      "Your account available balance INR 500",
      "UPI transaction pending. Rs 500 paid"
    ).forEach { assertNull(it, SmsPaymentParser.parseSmsText("BANK", it)) }
  }

  @Test fun pendingAndDecisionsSurviveRestartAndRescan() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("sms_payment_drafts", Context.MODE_PRIVATE).edit().clear().commit()
    val draft = SmsPaymentParser.parseSmsText("BANK", "INR 650 credited Ref 123456789012", 1700000000000L)!!
    assertTrue(SmsDraftStore(context).add(draft))
    assertEquals(listOf(draft), SmsDraftStore(context).pending())
    SmsDraftStore(context).decide(draft.id)
    assertFalse(SmsDraftStore(context).add(draft))
    assertTrue(SmsDraftStore(context).pending().isEmpty())
  }

  @Test fun stableIdentityForSameAlert() {
    val body = "Rs 300 paid via UPI Ref ABCD12345678"
    assertEquals(
      SmsPaymentParser.parseSmsText("BANK", body, 1000000L)?.id,
      SmsPaymentParser.parseSmsText("BANK", body, 1000020L)?.id
    )
  }
}
