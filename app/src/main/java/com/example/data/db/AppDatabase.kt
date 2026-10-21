package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BudgetDao
import com.example.data.dao.ClientDao
import com.example.data.dao.PaymentDraftDao
import com.example.data.dao.RecurringBillDao
import com.example.data.dao.TaskDao
import com.example.data.dao.TransactionDao
import com.example.data.dao.UpiAccountDao
import com.example.data.model.BudgetGoal
import com.example.data.model.Client
import com.example.data.model.NotificationDraft
import com.example.data.model.RecurringBill
import com.example.data.model.TaskItem
import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount

@Database(
  entities = [
    TaskItem::class,
    TransactionItem::class,
    UpiAccount::class,
    Client::class,
    RecurringBill::class,
    BudgetGoal::class,
    NotificationDraft::class
  ],
  version = 3,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun taskDao(): TaskDao
  abstract fun transactionDao(): TransactionDao
  abstract fun upiAccountDao(): UpiAccountDao
  abstract fun clientDao(): ClientDao
  abstract fun recurringBillDao(): RecurringBillDao
  abstract fun budgetDao(): BudgetDao
  abstract fun paymentDraftDao(): PaymentDraftDao

  companion object {
    /** Adds the payment drafts inbox without wiping the ledger. */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE IF NOT EXISTS `payment_drafts` (`id` TEXT NOT NULL, `senderApp` TEXT NOT NULL, " +
            "`amount` REAL NOT NULL, `type` TEXT NOT NULL, `rawText` TEXT NOT NULL, `senderOrReceiver` TEXT NOT NULL, " +
            "`timestamp` INTEGER NOT NULL, `upiReference` TEXT, `status` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
      }
    }

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "offline_ledger.db"
        )
          .addMigrations(MIGRATION_2_3)
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
