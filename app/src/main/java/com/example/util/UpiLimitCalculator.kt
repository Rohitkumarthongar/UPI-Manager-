package com.example.util

import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Encapsulates the real-time monthly and quarterly limit status for a UPI receiving account.
 */
data class UpiAccountLimitInfo(
  val account: UpiAccount,
  val monthlyLimit: Double,
  val monthlyUsed: Double,
  val monthlyRemaining: Double,
  val monthlyPercent: Float,
  val isMonthlyExceeded: Boolean,

  val quarterlyLimit: Double,
  val quarterlyUsed: Double,
  val quarterlyRemaining: Double,
  val quarterlyPercent: Float,
  val isQuarterlyExceeded: Boolean,

  val isStrictEnforcement: Boolean,
  val isRestricted: Boolean // True if active and either monthly or quarterly limit is breached
)

/**
 * Result of validating an attempted incoming transaction against a UPI account's configured limits.
 */
sealed class LimitValidationResult {
  object Allowed : LimitValidationResult()

  data class WarningOnly(
    val reason: String,
    val message: String
  ) : LimitValidationResult()

  data class Blocked(
    val reason: String,
    val period: String, // "Monthly" or "Quarterly"
    val limit: Double,
    val currentUsed: Double,
    val attemptedAmount: Double,
    val remaining: Double,
    val excess: Double,
    val alternativeAccounts: List<UpiAccount> = emptyList()
  ) : LimitValidationResult()
}

object UpiLimitCalculator {

  /**
   * Returns epoch timestamp of start of current calendar month (00:00:00.000).
   */
  fun getStartOfCurrentMonth(now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = now
      set(Calendar.DAY_OF_MONTH, 1)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  /**
   * Returns epoch timestamp of start of current calendar quarter (Jan 1, Apr 1, Jul 1, Oct 1).
   */
  fun getStartOfCurrentQuarter(now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = now
      val month = get(Calendar.MONTH) // 0-indexed: 0=Jan, 1=Feb, 2=Mar, 3=Apr...
      val quarterStartMonth = (month / 3) * 3
      set(Calendar.MONTH, quarterStartMonth)
      set(Calendar.DAY_OF_MONTH, 1)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  /**
   * Human readable quarter name (e.g., "Q3 (Jul - Sep)").
   */
  fun getCurrentQuarterName(now: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val month = cal.get(Calendar.MONTH)
    val quarterNum = (month / 3) + 1
    val range = when (quarterNum) {
      1 -> "Jan - Mar"
      2 -> "Apr - Jun"
      3 -> "Jul - Sep"
      else -> "Oct - Dec"
    }
    return "Q$quarterNum ($range)"
  }

  /**
   * Human readable current month name (e.g., "September 2026").
   */
  fun getCurrentMonthName(now: Long = System.currentTimeMillis()): String {
    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return sdf.format(Date(now))
  }

  /**
   * Computes current monthly and quarterly utilization for a given UPI account based on confirmed
   * incoming transactions.
   */
  fun calculateLimitInfo(
    account: UpiAccount,
    transactions: List<TransactionItem>,
    now: Long = System.currentTimeMillis()
  ): UpiAccountLimitInfo {
    val startOfMonth = getStartOfCurrentMonth(now)
    val startOfQuarter = getStartOfCurrentQuarter(now)

    // Sum all incoming payments credited to this specific UPI account in current month
    val monthlyUsed = transactions
      .filter { it.accountId == account.id && it.type == "INCOME" && it.timestamp >= startOfMonth }
      .sumOf { it.amount }

    // Sum all incoming payments credited in current quarter
    val quarterlyUsed = transactions
      .filter { it.accountId == account.id && it.type == "INCOME" && it.timestamp >= startOfQuarter }
      .sumOf { it.amount }

    val monthlyRemaining = if (account.monthlyLimit > 0) {
      maxOf(0.0, account.monthlyLimit - monthlyUsed)
    } else 0.0

    val monthlyPercent = if (account.monthlyLimit > 0) {
      (monthlyUsed / account.monthlyLimit).toFloat().coerceIn(0f, 1f)
    } else 0f

    val isMonthlyExceeded = account.monthlyLimit > 0 && monthlyUsed >= account.monthlyLimit

    val quarterlyRemaining = if (account.quarterlyLimit > 0) {
      maxOf(0.0, account.quarterlyLimit - quarterlyUsed)
    } else 0.0

    val quarterlyPercent = if (account.quarterlyLimit > 0) {
      (quarterlyUsed / account.quarterlyLimit).toFloat().coerceIn(0f, 1f)
    } else 0f

    val isQuarterlyExceeded = account.quarterlyLimit > 0 && quarterlyUsed >= account.quarterlyLimit

    val isRestricted = (isMonthlyExceeded || isQuarterlyExceeded) && account.isLimitEnforced

    return UpiAccountLimitInfo(
      account = account,
      monthlyLimit = account.monthlyLimit,
      monthlyUsed = monthlyUsed,
      monthlyRemaining = monthlyRemaining,
      monthlyPercent = monthlyPercent,
      isMonthlyExceeded = isMonthlyExceeded,
      quarterlyLimit = account.quarterlyLimit,
      quarterlyUsed = quarterlyUsed,
      quarterlyRemaining = quarterlyRemaining,
      quarterlyPercent = quarterlyPercent,
      isQuarterlyExceeded = isQuarterlyExceeded,
      isStrictEnforcement = account.isLimitEnforced,
      isRestricted = isRestricted
    )
  }

  /**
   * Validates if a transaction of [amount] can be accepted by [account].
   * If [account.isLimitEnforced] is true and [amount] pushes either monthly or quarterly total
   * over the configured limit, returns [LimitValidationResult.Blocked] along with candidate
   * alternative accounts that have sufficient remaining quota.
   */
  fun validateIncomingAmount(
    account: UpiAccount,
    amount: Double,
    transactions: List<TransactionItem>,
    allAccounts: List<UpiAccount> = emptyList(),
    now: Long = System.currentTimeMillis()
  ): LimitValidationResult {
    if (amount <= 0.0) return LimitValidationResult.Allowed

    val limitInfo = calculateLimitInfo(account, transactions, now)

    // 1. Check Monthly Limit
    if (account.monthlyLimit > 0.0) {
      val projectedMonthly = limitInfo.monthlyUsed + amount
      if (projectedMonthly > account.monthlyLimit) {
        val excess = projectedMonthly - account.monthlyLimit
        val alternatives = findEligibleAlternatives(amount, allAccounts, account.id, transactions, now)

        return if (account.isLimitEnforced) {
          LimitValidationResult.Blocked(
            reason = "Monthly receiving limit exceeded",
            period = "Monthly",
            limit = account.monthlyLimit,
            currentUsed = limitInfo.monthlyUsed,
            attemptedAmount = amount,
            remaining = limitInfo.monthlyRemaining,
            excess = excess,
            alternativeAccounts = alternatives
          )
        } else {
          LimitValidationResult.WarningOnly(
            reason = "Approaching monthly limit",
            message = "Transaction of ₹$amount exceeds monthly limit of ₹${account.monthlyLimit} by ₹${String.format(Locale.US, "%.2f", excess)}"
          )
        }
      }
    }

    // 2. Check Quarterly Limit
    if (account.quarterlyLimit > 0.0) {
      val projectedQuarterly = limitInfo.quarterlyUsed + amount
      if (projectedQuarterly > account.quarterlyLimit) {
        val excess = projectedQuarterly - account.quarterlyLimit
        val alternatives = findEligibleAlternatives(amount, allAccounts, account.id, transactions, now)

        return if (account.isLimitEnforced) {
          LimitValidationResult.Blocked(
            reason = "Quarterly receiving limit exceeded",
            period = "Quarterly",
            limit = account.quarterlyLimit,
            currentUsed = limitInfo.quarterlyUsed,
            attemptedAmount = amount,
            remaining = limitInfo.quarterlyRemaining,
            excess = excess,
            alternativeAccounts = alternatives
          )
        } else {
          LimitValidationResult.WarningOnly(
            reason = "Approaching quarterly limit",
            message = "Transaction of ₹$amount exceeds quarterly limit of ₹${account.quarterlyLimit} by ₹${String.format(Locale.US, "%.2f", excess)}"
          )
        }
      }
    }

    return LimitValidationResult.Allowed
  }

  private fun findEligibleAlternatives(
    amount: Double,
    allAccounts: List<UpiAccount>,
    currentAccountId: Long,
    transactions: List<TransactionItem>,
    now: Long
  ): List<UpiAccount> {
    return allAccounts
      .filter { it.id != currentAccountId }
      .filter { candidate ->
        val info = calculateLimitInfo(candidate, transactions, now)
        val fitsMonthly = candidate.monthlyLimit <= 0.0 || (info.monthlyUsed + amount <= candidate.monthlyLimit)
        val fitsQuarterly = candidate.quarterlyLimit <= 0.0 || (info.quarterlyUsed + amount <= candidate.quarterlyLimit)
        fitsMonthly && fitsQuarterly
      }
  }
}
