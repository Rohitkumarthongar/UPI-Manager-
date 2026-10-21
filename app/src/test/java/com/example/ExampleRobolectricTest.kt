package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CryptoManager
import com.example.data.model.Client
import com.example.data.model.TransactionItem
import com.example.ocr.ReceiptOcrHelper
import com.example.util.QrGenerator
import com.example.util.ReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app name string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Offline Ledger", appName)
  }

  @Test
  fun `test upi payload generation with amount`() {
    val payload = QrGenerator.buildUpiPayload(
      upiId = "store@okicici",
      payeeName = "Apex Store",
      amount = 750.50,
      note = "Invoice #1024"
    )
    assertTrue(payload.startsWith("upi://pay?"))
    assertTrue(payload.contains("pa=store%40okicici") || payload.contains("pa=store@okicici"))
    assertTrue(payload.contains("pn=Apex+Store") || payload.contains("pn=Apex%20Store"))
    assertTrue(payload.contains("am=750.50"))
    assertTrue(payload.contains("cu=INR"))
  }

  @Test
  fun `test receipt text parsing regex`() {
    val sampleText = """
      METRO SUPERMARKET
      Date: 12/09/2026
      Invoice No: INV-9821
      Items: Groceries
      TOTAL: Rs. 1,420.50
      Thank you for shopping!
    """.trimIndent()

    val parsed = ReceiptOcrHelper.extractReceiptDetails(sampleText)
    assertEquals("METRO SUPERMARKET", parsed.vendor)
    assertNotNull(parsed.amount)
    assertEquals(1420.50, parsed.amount!!, 0.01)
    assertEquals("INV-9821", parsed.invoiceNo)
  }

  @Test
  fun `test phonepe payment screenshot parsing`() {
    val samplePhonePeText = """
      Transaction Successful
      05:54 pm on 14 Aug 2026
      Paid to
      Puniya tyres
      paytmqr1rcvn1deli@paytm
      ₹22,000
      Transfer Details
      PhonePe Transaction ID
      T2608141754066026713528
      Debited from
      XXXXXX8787 ₹22,000
      UTR: 951886448560
      Powered by UPI AXIS BANK
    """.trimIndent()

    val parsed = ReceiptOcrHelper.extractReceiptDetails(samplePhonePeText)
    assertEquals("Puniya tyres", parsed.vendor)
    assertNotNull(parsed.amount)
    assertEquals(22000.0, parsed.amount!!, 0.01)
    assertEquals("EXPENSE", parsed.type)
    assertEquals("paytmqr1rcvn1deli@paytm", parsed.upiId)
    assertEquals("951886448560", parsed.invoiceNo)
    assertEquals("14 Aug 2026", parsed.dateString)
  }

  @Test
  fun `test crypto encryption roundtrip`() {
    val sensitiveFinancialNote = "Confidential Vendor Contract Advance ₹50,000"
    val encrypted = CryptoManager.encrypt(sensitiveFinancialNote)
    assertNotNull(encrypted)
    val decrypted = CryptoManager.decrypt(encrypted!!)
    assertEquals(sensitiveFinancialNote, decrypted)
  }

  @Test
  fun `test client balance sheet csv generation`() {
    val client = Client(
      id = 1L,
      name = "Apex Builders",
      phone = "9876543210",
      openingBalance = 5000.0
    )
    val transactions = listOf(
      TransactionItem(
        id = 101L,
        amount = 12000.0,
        type = "EXPENSE",
        category = "Hardware Material",
        clientId = 1L,
        note = "Order #1",
        timestamp = 1700000000000L
      ),
      TransactionItem(
        id = 102L,
        amount = 7000.0,
        type = "INCOME",
        category = "UPI Payment",
        clientId = 1L,
        note = "Part payment received",
        timestamp = 1700000100000L
      )
    )

    val csv = ReportExporter.generateClientBalanceSheetCsv(client, transactions)
    assertTrue(csv.contains("Apex Builders"))
    assertTrue(csv.contains("Opening Balance,5000.00"))
    assertTrue(csv.contains("12000.00"))
    assertTrue(csv.contains("7000.00"))
    assertTrue(csv.contains("CLOSING BALANCE") && csv.contains("10000.00"))
  }
}
