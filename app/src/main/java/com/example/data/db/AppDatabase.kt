package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
