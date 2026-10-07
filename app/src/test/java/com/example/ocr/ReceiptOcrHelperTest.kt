package com.example.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptOcrHelperTest {
  @Test fun totalSplitAcrossOcrBlocksIsParsed() {
    val receipt = ReceiptOcrHelper.extractReceiptDetails("Corner Store\nGrand Total\n1,250.50\nBalance ₹99,000")
    assertEquals(1250.50, receipt.amount!!, 0.001)
  }

  @Test fun paymentScreenshotIgnoresAccountAndBalance() {
    val receipt = ReceiptOcrHelper.extractReceiptDetails("""
      Payment successful
      Paid to
      Corner Store
      ₹450.00
      Debited from 987654321012 ₹450.00
      Available balance ₹82,500.00
      UTR: 123456789012
    """.trimIndent())

    assertEquals(450.0, receipt.amount!!, 0.001)
    assertEquals("Corner Store", receipt.vendor)
    assertEquals("123456789012", receipt.invoiceNo)
    assertNull(receipt.extractionError)
  }

  @Test fun receiptUsesTotalRatherThanLargerSubtotal() {
    val receipt = ReceiptOcrHelper.extractReceiptDetails("""
      METRO SUPERMARKET
      Subtotal Rs. 1200.00
      Discount Rs. 300.00
      Grand Total Rs. 900.00
      Balance Rs. 50000.00
    """.trimIndent())

    assertEquals(900.0, receipt.amount!!, 0.001)
  }

  @Test fun referenceAndDateWithoutMoneyNeverBecomeAmount() {
    val receipt = ReceiptOcrHelper.extractReceiptDetails("""
      Payment successful
      Transaction ID: 260814175406
      14/08/2026
      Account 9988776655
      Available balance 25000
    """.trimIndent())

    assertNull(receipt.amount)
    assertTrue(receipt.extractionError!!.contains("amount"))
  }

  @Test fun blankOcrHasActionableFailure() {
    val receipt = ReceiptOcrHelper.extractReceiptDetails("  \n ")
    assertNull(receipt.amount)
    assertTrue(receipt.extractionError!!.contains("clearer image"))
  }

  @Test fun receivedPaymentIsIncomeAndPicksReceivedAmount() {
    val receipt = ReceiptOcrHelper.extractReceiptDetails("""
      Payment received from
      Riya
      Amount received ₹1,250.50
      Available balance ₹99,000.00
    """.trimIndent())

    assertEquals("INCOME", receipt.type)
    assertEquals(1250.50, receipt.amount!!, 0.001)
  }

  @Test fun paidReceiptDoesNotUseAmountDueAsPaid() {
    val receipt = ReceiptOcrHelper.extractReceiptDetails("""
      Paid ₹300
      Amount due ₹1200
      Invoice No: INV-12345
    """.trimIndent())

    assertEquals(300.0, receipt.amount!!, 0.001)
  }

  @Test fun indianGroupedCurrencyIsParsedWhole() {
    val receipt = ReceiptOcrHelper.extractReceiptDetails("Payment successful\n₹1,23,456.75\nUTR: 123456789012")
    assertEquals(123456.75, receipt.amount!!, 0.001)
  }

  @Test fun creditCardPaymentRemainsExpense() {
    val receipt = ReceiptOcrHelper.extractReceiptDetails("Paid using credit card\nAmount paid ₹700")
    assertEquals("EXPENSE", receipt.type)
    assertEquals(700.0, receipt.amount!!, 0.001)
  }
}
