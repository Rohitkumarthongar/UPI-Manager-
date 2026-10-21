package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.model.BudgetGoal
import com.example.data.model.Client
import com.example.data.model.RecurringBill
import com.example.data.model.TaskItem
import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EmptyLedgerDatabaseTest {
  @Test
  fun newInstallIsEmptyAndOldSeedLookalikesRemainUserDataAfterReopen() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.deleteDatabase("offline_ledger.db")
    val db = AppDatabase.getInstance(context)
    try {
      assertTrue(db.taskDao().getAllTasks().first().isEmpty())
      assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
      assertTrue(db.upiAccountDao().getAllAccounts().first().isEmpty())
      assertTrue(db.clientDao().getAllClients().first().isEmpty())
      assertTrue(db.recurringBillDao().getAllBills().first().isEmpty())
      assertTrue(db.budgetDao().getAllBudgets().first().isEmpty())
      assertEquals(0.0, db.transactionDao().getAllTransactions().first().sumOf { it.amount }, 0.0)

      // A name/amount from an older demonstration is not proof of provenance.
      db.taskDao().insertTask(TaskItem(title = "Audit monthly balance sheets with clients"))
      db.transactionDao().insertTransaction(TransactionItem(amount = 45000.0, type = "INCOME", category = "Sales"))
      db.upiAccountDao().insertAccount(UpiAccount(upiId = "merchant.store@okhdfcbank", payeeName = "Offline Ledger Store", label = "Store Primary HDFC"))
      db.clientDao().insertClient(Client(name = "Apex Retail Corp"))
      db.recurringBillDao().insertBill(RecurringBill(title = "Office Space Rent", amount = 25000.0, dueDayOfMonth = 1))
      db.budgetDao().insertBudget(BudgetGoal(category = "Inventory", monthlyLimit = 40000.0))
    } finally {
      db.close()
    }

    val reopened = Room.databaseBuilder(context, AppDatabase::class.java, "offline_ledger.db").build()
    try {
      assertEquals(1, reopened.taskDao().getAllTasks().first().size)
      assertEquals(1, reopened.transactionDao().getAllTransactions().first().size)
      assertEquals(1, reopened.upiAccountDao().getAllAccounts().first().size)
      assertEquals(1, reopened.clientDao().getAllClients().first().size)
      assertEquals(1, reopened.recurringBillDao().getAllBills().first().size)
      assertEquals(1, reopened.budgetDao().getAllBudgets().first().size)
    } finally {
      reopened.close()
      context.deleteDatabase("offline_ledger.db")
    }
  }
}
