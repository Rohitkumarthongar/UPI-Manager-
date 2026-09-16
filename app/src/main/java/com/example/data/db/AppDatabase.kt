package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BudgetDao
import com.example.data.dao.ClientDao
import com.example.data.dao.RecurringBillDao
import com.example.data.dao.TaskDao
import com.example.data.dao.TransactionDao
import com.example.data.dao.UpiAccountDao
import com.example.data.model.BudgetGoal
import com.example.data.model.Client
import com.example.data.model.RecurringBill
import com.example.data.model.TaskItem
import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
  entities = [
    TaskItem::class,
    TransactionItem::class,
    UpiAccount::class,
    Client::class,
    RecurringBill::class,
    BudgetGoal::class
  ],
  version = 2,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun taskDao(): TaskDao
  abstract fun transactionDao(): TransactionDao
  abstract fun upiAccountDao(): UpiAccountDao
  abstract fun clientDao(): ClientDao
  abstract fun recurringBillDao(): RecurringBillDao
  abstract fun budgetDao(): BudgetDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "offline_ledger.db"
        )
          .addCallback(DatabaseCallback())
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }
  }

  private class DatabaseCallback : Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
      super.onCreate(db)
      INSTANCE?.let { database ->
        CoroutineScope(Dispatchers.IO).launch {
          populateInitialData(database)
        }
      }
    }

    private suspend fun populateInitialData(db: AppDatabase) {
      // 1. Initial UPI Accounts with Monthly & Quarterly receiving limits
      val acc1Id = db.upiAccountDao().insertAccount(
        UpiAccount(
          upiId = "merchant.store@okhdfcbank",
          payeeName = "Offline Ledger Store",
          label = "Store Primary HDFC",
          bankName = "HDFC Bank",
          isActive = true,
          monthlyLimit = 60000.0,
          quarterlyLimit = 180000.0,
          isLimitEnforced = true
        )
      )
      val acc2Id = db.upiAccountDao().insertAccount(
        UpiAccount(
          upiId = "billing.services@icici",
          payeeName = "Rohit Financials",
          label = "Commercial Current ICICI",
          bankName = "ICICI Bank",
          isActive = false,
          monthlyLimit = 100000.0,
          quarterlyLimit = 300000.0,
          isLimitEnforced = true
        )
      )
      val acc3Id = db.upiAccountDao().insertAccount(
        UpiAccount(
          upiId = "instantpay@axisbank",
          payeeName = "Rohit Soni",
          label = "Personal Instant Axis",
          bankName = "Axis Bank",
          isActive = false,
          monthlyLimit = 25000.0,
          quarterlyLimit = 75000.0,
          isLimitEnforced = false
        )
      )

      // 2. Clients
      val c1Id = db.clientDao().insertClient(
        Client(
          name = "Apex Retail Corp",
          phone = "+91 98234 56789",
          email = "accounts@apexretail.in",
          openingBalance = 15400.0,
          notes = "B2B client. Net-30 payment terms."
        )
      )
      val c2Id = db.clientDao().insertClient(
        Client(
          name = "Sunita Studio & Design",
          phone = "+91 94112 33445",
          email = "sunita@designhub.com",
          openingBalance = -3200.0,
          notes = "Advance retainer deposit on file."
        )
      )
      val c3Id = db.clientDao().insertClient(
        Client(
          name = "Metro Tech Supplies",
          phone = "+91 91223 88990",
          email = "orders@metrotech.org",
          openingBalance = 45000.0,
          notes = "Bulk hardware and hardware procurement."
        )
      )

      val now = System.currentTimeMillis()
      val day = 86400000L

      // 3. Transactions
      db.transactionDao().insertTransaction(
        TransactionItem(
          amount = 45000.0,
          type = "INCOME",
          category = "Sales",
          accountId = acc1Id,
          clientId = c1Id,
          note = "Invoice #APX-204 Payment received via UPI",
          source = "MANUAL",
          timestamp = now - (day * 1)
        )
      )
      db.transactionDao().insertTransaction(
        TransactionItem(
          amount = 12500.0,
          type = "EXPENSE",
          category = "Inventory",
          accountId = acc1Id,
          clientId = c3Id,
          note = "Stock reorder invoice #MET-883",
          source = "OCR",
          timestamp = now - (day * 2)
        )
      )
      db.transactionDao().insertTransaction(
        TransactionItem(
          amount = 28000.0,
          type = "INCOME",
          category = "Services",
          accountId = acc2Id,
          clientId = c2Id,
          note = "UI UX Consulting phase 1 milestone",
          source = "NOTIFICATION",
          timestamp = now - (day * 4)
        )
      )
      db.transactionDao().insertTransaction(
        TransactionItem(
          amount = 3450.0,
          type = "EXPENSE",
          category = "Utilities",
          accountId = acc1Id,
          note = "Office High-Speed Fiber Internet Bill",
          source = "MANUAL",
          timestamp = now - (day * 6)
        )
      )
      db.transactionDao().insertTransaction(
        TransactionItem(
          amount = 8900.0,
          type = "INCOME",
          category = "Sales",
          accountId = acc1Id,
          note = "Walk-in client UPI payment",
          source = "NOTIFICATION",
          timestamp = now - (day * 8)
        )
      )

      // 4. Tasks
      db.taskDao().insertTask(
        TaskItem(
          title = "Audit monthly balance sheets with clients",
          description = "Generate PDF reports for Apex Retail and Metro Tech",
          dueDate = now + (3600000L * 4),
          priority = "HIGH",
          category = "Finance",
          progressPercent = 60,
          streakCount = 4
        )
      )
      db.taskDao().insertTask(
        TaskItem(
          title = "Scan recent hardware purchase invoices",
          description = "Use OCR to autofill ledger entries",
          dueDate = now + (3600000L * 7),
          priority = "MEDIUM",
          category = "Work",
          progressPercent = 20,
          streakCount = 2
        )
      )
      db.taskDao().insertTask(
        TaskItem(
          title = "Verify UPI payment confirmations",
          description = "Check incoming notifications inbox for unconfirmed drafts",
          dueDate = now + (3600000L * 12),
          priority = "MEDIUM",
          category = "Finance",
          progressPercent = 0,
          streakCount = 7
        )
      )
      db.taskDao().insertTask(
        TaskItem(
          title = "Electricity & Server recurring bill review",
          description = "Confirm auto-log against monthly budget limits",
          dueDate = now + (day * 2),
          priority = "LOW",
          category = "Finance",
          progressPercent = 100,
          isDone = true,
          streakCount = 3
        )
      )

      // 5. Recurring Bills
      db.recurringBillDao().insertBill(
        RecurringBill(
          title = "Office Space Rent",
          amount = 25000.0,
          dueDayOfMonth = 1,
          category = "Rent",
          reminderDaysBefore = 3,
          upiAccountId = acc1Id
        )
      )
      db.recurringBillDao().insertBill(
        RecurringBill(
          title = "Business Broadband & Telecom",
          amount = 3450.0,
          dueDayOfMonth = 10,
          category = "Utilities",
          reminderDaysBefore = 2,
          upiAccountId = acc1Id
        )
      )
      db.recurringBillDao().insertBill(
        RecurringBill(
          title = "Cloud Infrastructure & Domain",
          amount = 4800.0,
          dueDayOfMonth = 18,
          category = "Services",
          reminderDaysBefore = 2,
          upiAccountId = acc2Id
        )
      )

      // 6. Budgets
      db.budgetDao().insertBudget(BudgetGoal(category = "Inventory", monthlyLimit = 40000.0))
      db.budgetDao().insertBudget(BudgetGoal(category = "Utilities", monthlyLimit = 8000.0))
      db.budgetDao().insertBudget(BudgetGoal(category = "Services", monthlyLimit = 15000.0))
      db.budgetDao().insertBudget(BudgetGoal(category = "Rent", monthlyLimit = 25000.0))
      db.budgetDao().insertBudget(BudgetGoal(category = "Other", monthlyLimit = 10000.0))
    }
  }
}
