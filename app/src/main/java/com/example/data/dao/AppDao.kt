package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.BudgetGoal
import com.example.data.model.Client
import com.example.data.model.RecurringBill
import com.example.data.model.TaskItem
import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
  @Query("SELECT * FROM tasks ORDER BY isDone ASC, dueDate ASC, id DESC")
  fun getAllTasks(): Flow<List<TaskItem>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTask(task: TaskItem): Long

  @Update
  suspend fun updateTask(task: TaskItem)

  @Delete
  suspend fun deleteTask(task: TaskItem)
}

@Dao
interface TransactionDao {
  @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
  fun getAllTransactions(): Flow<List<TransactionItem>>

  @Query("SELECT * FROM transactions WHERE clientId = :clientId ORDER BY timestamp ASC, id ASC")
  fun getTransactionsForClient(clientId: Long): Flow<List<TransactionItem>>

  @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
  fun getTransactionsForAccount(accountId: Long): Flow<List<TransactionItem>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTransaction(item: TransactionItem): Long

  @Update
  suspend fun updateTransaction(item: TransactionItem)

  @Delete
  suspend fun deleteTransaction(item: TransactionItem)

  @Query("DELETE FROM transactions WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface UpiAccountDao {
  @Query("SELECT * FROM upi_accounts ORDER BY isActive DESC, id ASC")
  fun getAllAccounts(): Flow<List<UpiAccount>>

  @Query("SELECT * FROM upi_accounts WHERE isActive = 1 LIMIT 1")
  fun getActiveAccount(): Flow<UpiAccount?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccount(account: UpiAccount): Long

  @Update
  suspend fun updateAccount(account: UpiAccount)

  @Delete
  suspend fun deleteAccount(account: UpiAccount)

  @Query("UPDATE upi_accounts SET isActive = 0")
  suspend fun clearActiveAccounts()

  @Query("UPDATE upi_accounts SET isActive = 1 WHERE id = :id")
  suspend fun setActive(id: Long)

  @Transaction
  suspend fun switchActiveAccount(id: Long) {
    clearActiveAccounts()
    setActive(id)
  }
}

@Dao
interface ClientDao {
  @Query("SELECT * FROM clients ORDER BY name ASC")
  fun getAllClients(): Flow<List<Client>>

  @Query("SELECT * FROM clients WHERE id = :id")
  fun getClientById(id: Long): Flow<Client?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertClient(client: Client): Long

  @Update
  suspend fun updateClient(client: Client)

  @Delete
  suspend fun deleteClient(client: Client)
}

@Dao
interface RecurringBillDao {
  @Query("SELECT * FROM recurring_bills ORDER BY dueDayOfMonth ASC")
  fun getAllBills(): Flow<List<RecurringBill>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBill(bill: RecurringBill): Long

  @Update
  suspend fun updateBill(bill: RecurringBill)

  @Delete
  suspend fun deleteBill(bill: RecurringBill)
}

@Dao
interface BudgetDao {
  @Query("SELECT * FROM budgets ORDER BY monthlyLimit DESC")
  fun getAllBudgets(): Flow<List<BudgetGoal>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBudget(budget: BudgetGoal): Long

  @Update
  suspend fun updateBudget(budget: BudgetGoal)

  @Delete
  suspend fun deleteBudget(budget: BudgetGoal)
}
