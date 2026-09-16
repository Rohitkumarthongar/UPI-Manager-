package com.example.data.repository

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
import kotlinx.coroutines.flow.Flow

class AppRepository(
  private val taskDao: TaskDao,
  private val transactionDao: TransactionDao,
  private val upiAccountDao: UpiAccountDao,
  private val clientDao: ClientDao,
  private val recurringBillDao: RecurringBillDao,
  private val budgetDao: BudgetDao
) {
  // Tasks
  val allTasks: Flow<List<TaskItem>> = taskDao.getAllTasks()
  suspend fun insertTask(task: TaskItem) = taskDao.insertTask(task)
  suspend fun updateTask(task: TaskItem) = taskDao.updateTask(task)
  suspend fun deleteTask(task: TaskItem) = taskDao.deleteTask(task)

  // Transactions
  val allTransactions: Flow<List<TransactionItem>> = transactionDao.getAllTransactions()
  fun getTransactionsForClient(clientId: Long) = transactionDao.getTransactionsForClient(clientId)
  fun getTransactionsForAccount(accountId: Long) = transactionDao.getTransactionsForAccount(accountId)
  suspend fun insertTransaction(item: TransactionItem) = transactionDao.insertTransaction(item)
  suspend fun updateTransaction(item: TransactionItem) = transactionDao.updateTransaction(item)
  suspend fun deleteTransaction(item: TransactionItem) = transactionDao.deleteTransaction(item)

  // UPI Accounts
  val allUpiAccounts: Flow<List<UpiAccount>> = upiAccountDao.getAllAccounts()
  val activeAccount: Flow<UpiAccount?> = upiAccountDao.getActiveAccount()
  suspend fun insertAccount(account: UpiAccount) = upiAccountDao.insertAccount(account)
  suspend fun updateAccount(account: UpiAccount) = upiAccountDao.updateAccount(account)
  suspend fun deleteAccount(account: UpiAccount) = upiAccountDao.deleteAccount(account)
  suspend fun switchActiveAccount(id: Long) = upiAccountDao.switchActiveAccount(id)

  // Clients
  val allClients: Flow<List<Client>> = clientDao.getAllClients()
  fun getClientById(id: Long) = clientDao.getClientById(id)
  suspend fun insertClient(client: Client) = clientDao.insertClient(client)
  suspend fun updateClient(client: Client) = clientDao.updateClient(client)
  suspend fun deleteClient(client: Client) = clientDao.deleteClient(client)

  // Recurring Bills
  val allBills: Flow<List<RecurringBill>> = recurringBillDao.getAllBills()
  suspend fun insertBill(bill: RecurringBill) = recurringBillDao.insertBill(bill)
  suspend fun updateBill(bill: RecurringBill) = recurringBillDao.updateBill(bill)
  suspend fun deleteBill(bill: RecurringBill) = recurringBillDao.deleteBill(bill)

  // Budgets
  val allBudgets: Flow<List<BudgetGoal>> = budgetDao.getAllBudgets()
  suspend fun insertBudget(budget: BudgetGoal) = budgetDao.insertBudget(budget)
  suspend fun updateBudget(budget: BudgetGoal) = budgetDao.updateBudget(budget)
  suspend fun deleteBudget(budget: BudgetGoal) = budgetDao.deleteBudget(budget)

  /**
   * Helper to mark a recurring bill as paid and automatically insert
   * the corresponding expense into the transaction ledger.
   */
  suspend fun payBill(bill: RecurringBill, upiAccountId: Long?) {
    val txn = TransactionItem(
      amount = bill.amount,
      type = "EXPENSE",
      category = bill.category,
      accountId = upiAccountId ?: bill.upiAccountId,
      note = "Auto-settled recurring bill: ${bill.title}",
      source = "MANUAL",
      status = "CONFIRMED",
      timestamp = System.currentTimeMillis()
    )
    insertTransaction(txn)
    updateBill(bill.copy(lastPaidDate = System.currentTimeMillis()))
  }
}
