package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Task item for daily checklist, recurring habits, and progress tracking.
 */
@Entity(tableName = "tasks")
data class TaskItem(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val description: String = "",
  val dueDate: Long = System.currentTimeMillis(),
  val reminderEnabled: Boolean = true,
  val recurrence: String = "DAILY", // NONE, DAILY, WEEKLY, MONTHLY
  val progressPercent: Int = 0, // 0 to 100
  val isDone: Boolean = false,
  val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
  val category: String = "Work", // Work, Finance, Personal, Shopping
  val streakCount: Int = 0,
  val completedAt: Long? = null
)

/**
 * Financial transaction entity recording income, expense, linked account, client, and receipt.
 */
@Entity(tableName = "transactions")
data class TransactionItem(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val amount: Double,
  val type: String, // "INCOME", "EXPENSE"
  val category: String, // Sales, Services, Inventory, Rent, Food, Utilities, Travel, Salary, Other
  val accountId: Long? = null, // Linked UPI Account
  val clientId: Long? = null, // Linked Client ledger
  val note: String = "",
  val encryptedNote: String? = null, // Keystore AES-encrypted sensitive payload
  val receiptImagePath: String? = null,
  val source: String = "MANUAL", // MANUAL, OCR, NOTIFICATION
  val status: String = "CONFIRMED", // CONFIRMED, DRAFT
  val timestamp: Long = System.currentTimeMillis(),
  val vendorName: String? = null,
  val referenceNumber: String? = null
)

/**
 * UPI receiving accounts for multi-account switching and dynamic QR generation.
 */
@Entity(tableName = "upi_accounts")
data class UpiAccount(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val upiId: String,
  val payeeName: String,
  val label: String, // e.g., "Current Account", "Shop QR", "Personal"
  val bankName: String = "",
  val isActive: Boolean = false, // True for active receiving QR
  val monthlyLimit: Double = 0.0, // 0.0 = unlimited, otherwise max accepted ₹ per month
  val quarterlyLimit: Double = 0.0, // 0.0 = unlimited, otherwise max accepted ₹ per quarter
  val isLimitEnforced: Boolean = true, // If true, strictly blocks transactions exceeding limit
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * Client profile for managing business ledgers and audit balance sheets.
 */
@Entity(tableName = "clients")
data class Client(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val phone: String = "",
  val email: String = "",
  val openingBalance: Double = 0.0, // Positive = Client owes us (Receivable), Negative = We owe client (Payable)
  val notes: String = "",
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * Recurring bills with lead-time reminders and auto-ledger logging.
 */
@Entity(tableName = "recurring_bills")
data class RecurringBill(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val amount: Double,
  val dueDayOfMonth: Int, // 1 to 31
  val category: String = "Utilities",
  val reminderDaysBefore: Int = 3,
  val lastPaidDate: Long? = null,
  val upiAccountId: Long? = null
)

/**
 * Category-based monthly budget goals for spending limits and alerts.
 */
@Entity(tableName = "budgets")
data class BudgetGoal(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val category: String,
  val monthlyLimit: Double
)

/**
 * Draft transaction parsed from payment confirmations or notification listener.
 */
data class NotificationDraft(
  val id: String,
  val senderApp: String, // GPay, PhonePe, Paytm, HDFC Bank, etc.
  val amount: Double,
  val type: String, // "INCOME" or "EXPENSE"
  val rawText: String,
  val senderOrReceiver: String,
  val timestamp: Long = System.currentTimeMillis(),
  val upiReference: String? = null
)
