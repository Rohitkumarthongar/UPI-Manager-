package com.example

import com.example.payments.PaymentMessageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentMessageParserTest {

  @Test fun hdfcCreditSms() {
    val p = PaymentMessageParser.parseMessage(
      "Rs.1,250.00 credited to HDFC Bank A/c XX1234 on 20-10-26 from VPA rahul.s@okicici (UPI 429812345678)"
    )!!
    assertEquals(1250.0, p.amount, 0.001)
    assertEquals("INCOME", p.type)
    assertEquals("429812345678", p.reference)
    assertEquals("rahul.s@okicici", p.counterparty)
  }

  @Test fun sbiCreditWithoutCurrencySign() {
    val p = PaymentMessageParser.parseMessage(
      "Dear SBI UPI User, ur A/cX5678 credited by Rs500 on 20Oct26 by (Ref no 429899998888)"
    )!!
    assertEquals(500.0, p.amount, 0.001)
    assertEquals("INCOME", p.type)
    assertEquals("429899998888", p.reference)
  }

  @Test fun sbiDebit() {
    val p = PaymentMessageParser.parseMessage(
      "Dear UPI user A/C X5678 debited by 320.0 on date 20Oct26 trf to CHAI POINT Refno 429811112222. If not u? call 1800111109"
    )!!
    assertEquals(320.0, p.amount, 0.001)
    assertEquals("EXPENSE", p.type)
    assertEquals("CHAI POINT", p.counterparty)
  }

  @Test fun gpayNotification() {
    val p = PaymentMessageParser.parseMessage("Rahul Sharma paid you ₹500")!!
    assertEquals(500.0, p.amount, 0.001)
    assertEquals("INCOME", p.type)
    assertEquals("Rahul Sharma", p.counterparty)
  }

  @Test fun phonePeNotification() {
    val p = PaymentMessageParser.parseMessage("Received ₹2,000 from Sunita Verma\nMoney received in your account")!!
    assertEquals(2000.0, p.amount, 0.001)
    assertEquals("INCOME", p.type)
    assertEquals("Sunita Verma", p.counterparty)
  }

  @Test fun debitWithCreditedPayeeIsExpense() {
    val p = PaymentMessageParser.parseMessage(
      "Acct XX123 debited for Rs 450.00 on 20-Oct-26; Chai Point credited. UPI:429812340000"
    )!!
    assertEquals("EXPENSE", p.type)
    assertEquals(450.0, p.amount, 0.001)
  }

  @Test fun rejectsOtpAndRequests() {
    assertNull(PaymentMessageParser.parseMessage("123456 is your OTP for payment of Rs 500 to Amazon"))
    assertNull(PaymentMessageParser.parseMessage("Rahul has requested ₹500 from you on Google Pay"))
    assertNull(PaymentMessageParser.parseMessage("Your payment of Rs 200 failed. Amount will be refunded"))
    assertNull(PaymentMessageParser.parseMessage("Hello, how are you?"))
  }

  @Test fun gpayScreenshotFromCustomerPhone() {
    val ocr = """
      ₹1,500
      Paid to
      Sharma Hardware
      Completed
      20 Oct 2026, 4:15 pm
      From: RAJESH VERMA (State Bank of India)
      To: sharma.hw@okhdfcbank
      UPI transaction ID
      429855556666
    """.trimIndent()
    val p = PaymentMessageParser.parseScreenshot(ocr)!!
    assertEquals(1500.0, p.amount, 0.001)
    assertEquals("429855556666", p.reference)
    assertEquals("RAJESH VERMA (State Bank of India)", p.fromParty)
    assertNotNull(p.vpas.firstOrNull { it == "sharma.hw@okhdfcbank" })
  }

  @Test fun screenshotWithMisreadRupeeSign() {
    val p = PaymentMessageParser.parseScreenshot("Payment Successful\n?850\nReceived from\nAnil Kumar\nUTR: 429877778888")!!
    assertEquals(850.0, p.amount, 0.001)
    assertEquals("INCOME", p.type)
    assertEquals("Anil Kumar", p.counterparty)
    assertEquals("429877778888", p.reference)
  }
}
