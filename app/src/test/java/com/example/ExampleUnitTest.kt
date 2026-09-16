package com.example

import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import com.example.util.LimitValidationResult
import com.example.util.UpiLimitCalculator
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testUpiLimitCalculation_withinQuota() {
    val account = UpiAccount(
      id = 1,
      upiId = "store@bank",
      payeeName = "Store",
      label = "Current",
      monthlyLimit = 50000.0,
      quarterlyLimit = 150000.0,
      isLimitEnforced = true
    )

    val txns = listOf(
      TransactionItem(
        id = 10,
        amount = 12000.0,
        type = "INCOME",
        category = "Sales",
        accountId = 1,
        timestamp = System.currentTimeMillis()
      )
    )

    val info = UpiLimitCalculator.calculateLimitInfo(account, txns)
    assertEquals(12000.0, info.monthlyUsed, 0.001)
    assertEquals(38000.0, info.monthlyRemaining, 0.001)
    assertFalse(info.isMonthlyExceeded)
    assertFalse(info.isRestricted)

    // Allowed transaction
    val result = UpiLimitCalculator.validateIncomingAmount(account, 10000.0, txns)
    assertTrue(result is LimitValidationResult.Allowed)
  }

  @Test
  fun testUpiLimitCalculation_exceedsMonthly_isBlocked() {
    val account = UpiAccount(
      id = 1,
      upiId = "store@bank",
      payeeName = "Store",
      label = "Store Account",
      monthlyLimit = 20000.0,
      quarterlyLimit = 60000.0,
      isLimitEnforced = true
    )

    val altAccount = UpiAccount(
      id = 2,
      upiId = "alt@bank",
      payeeName = "Store Alt",
      label = "Alt Account",
      monthlyLimit = 100000.0,
      quarterlyLimit = 300000.0,
      isLimitEnforced = true
    )

    val txns = listOf(
      TransactionItem(
        id = 10,
        amount = 15000.0,
        type = "INCOME",
        category = "Sales",
        accountId = 1,
        timestamp = System.currentTimeMillis()
      )
    )

    // Attempting 10,000 exceeds 20,000 cap by 5,000
    val result = UpiLimitCalculator.validateIncomingAmount(
      account = account,
      amount = 10000.0,
      transactions = txns,
      allAccounts = listOf(account, altAccount)
    )

    assertTrue("Should be blocked", result is LimitValidationResult.Blocked)
    val blocked = result as LimitValidationResult.Blocked
    assertEquals("Monthly", blocked.period)
    assertEquals(5000.0, blocked.excess, 0.001)
    assertEquals(1, blocked.alternativeAccounts.size)
    assertEquals(2L, blocked.alternativeAccounts[0].id)
  }
}

