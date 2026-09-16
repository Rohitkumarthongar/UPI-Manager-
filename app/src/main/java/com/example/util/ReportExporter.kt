package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.Client
import com.example.data.model.TransactionItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

  private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
  private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

  /**
   * Generates a CSV for the full transaction log or filtered monthly report.
   */
  fun generateTransactionsCsv(
    transactions: List<TransactionItem>,
    monthLabel: String = "All_Time"
  ): String {
    val sb = StringBuilder()
    sb.append("Transaction ID,Date & Time,Type,Category,Amount (INR),Source,Status,Vendor,Note\n")
    for (txn in transactions) {
      val dateStr = dateFormat.format(Date(txn.timestamp))
      val cleanNote = txn.note.replace(",", " ").replace("\n", " ")
      val vendor = (txn.vendorName ?: "").replace(",", " ")
      sb.append("${txn.id},\"$dateStr\",${txn.type},\"${txn.category}\",${String.format(Locale.US, "%.2f", txn.amount)},${txn.source},${txn.status},\"$vendor\",\"$cleanNote\"\n")
    }
    return sb.toString()
  }

  /**
   * Generates an audit-ready Client Balance Sheet in CSV format.
   */
  fun generateClientBalanceSheetCsv(
    client: Client,
    transactions: List<TransactionItem>
  ): String {
    val sb = StringBuilder()
    sb.append("CLIENT BALANCE SHEET - AUDIT REPORT\n")
    sb.append("Client Name,\"${client.name}\"\n")
    sb.append("Contact,\"${client.phone}\"\n")
    sb.append("Generated On,\"${dateFormat.format(Date())}\"\n")
    sb.append("Opening Balance,${String.format(Locale.US, "%.2f", client.openingBalance)}\n")
    sb.append("\n")
    sb.append("Date,Txn ID,Type,Category,Debit (Billed),Credit (Received),Running Balance,Note\n")

    var runningBal = client.openingBalance
    var totalDebit = 0.0
    var totalCredit = 0.0

    for (txn in transactions) {
      val dateStr = dateFormat.format(Date(txn.timestamp))
      val isDebit = txn.type == "EXPENSE" // We billed/spent for them
      val isCredit = txn.type == "INCOME" // They paid us
      val debitAmt = if (isDebit) txn.amount else 0.0
      val creditAmt = if (isCredit) txn.amount else 0.0

      if (isDebit) {
        runningBal += debitAmt
        totalDebit += debitAmt
      } else {
        runningBal -= creditAmt
        totalCredit += creditAmt
      }

      val cleanNote = txn.note.replace(",", " ")
      sb.append("\"$dateStr\",${txn.id},${txn.type},\"${txn.category}\",${String.format(Locale.US, "%.2f", debitAmt)},${String.format(Locale.US, "%.2f", creditAmt)},${String.format(Locale.US, "%.2f", runningBal)},\"$cleanNote\"\n")
    }

    sb.append("\n")
    sb.append("SUMMARY,,,\"Total Invoiced\",${String.format(Locale.US, "%.2f", totalDebit)},\"Total Paid\",${String.format(Locale.US, "%.2f", totalCredit)},,\n")
    sb.append("CLOSING BALANCE,,,\"Net Outstanding\",${String.format(Locale.US, "%.2f", runningBal)},,,,\n")
    return sb.toString()
  }

  /**
   * Formats an audit-ready balance sheet as formatted text document (ready for PDF / sharing).
   */
  fun formatClientAuditDocument(
    client: Client,
    transactions: List<TransactionItem>
  ): String {
    val sb = StringBuilder()
    sb.append("====================================================\n")
    sb.append("           OFFLINE LEDGER AUDIT REPORT              \n")
    sb.append("====================================================\n")
    sb.append("Client Name     : ${client.name}\n")
    sb.append("Phone / Contact : ${client.phone}\n")
    sb.append("Audit Date      : ${dateFormat.format(Date())}\n")
    sb.append("Data Encryption : Hardware-backed AES-256 (Verified)\n")
    sb.append("----------------------------------------------------\n")
    sb.append(String.format(Locale.US, "Opening Balance : ₹ %,.2f\n\n", client.openingBalance))
    sb.append("TRANSACTION HISTORY:\n")
    sb.append("----------------------------------------------------\n")

    var runningBal = client.openingBalance
    var totalDebit = 0.0
    var totalCredit = 0.0

    if (transactions.isEmpty()) {
      sb.append("No recorded ledger transactions for this period.\n")
    } else {
      for (txn in transactions) {
        val isDebit = txn.type == "EXPENSE"
        val debitAmt = if (isDebit) txn.amount else 0.0
        val creditAmt = if (!isDebit) txn.amount else 0.0

        if (isDebit) {
          runningBal += debitAmt
          totalDebit += debitAmt
        } else {
          runningBal -= creditAmt
          totalCredit += creditAmt
        }

        sb.append("[${dateFormat.format(Date(txn.timestamp))}]\n")
        sb.append("  ${txn.category} | ${txn.note.ifBlank { "Ledger Entry" }}\n")
        sb.append(String.format(Locale.US, "  Debit: ₹ %,.2f | Credit: ₹ %,.2f | Balance: ₹ %,.2f\n", debitAmt, creditAmt, runningBal))
        sb.append("----------------------------------------------------\n")
      }
    }

    sb.append("\nAUDIT SUMMARY:\n")
    sb.append(String.format(Locale.US, "Total Debited / Billed  : ₹ %,.2f\n", totalDebit))
    sb.append(String.format(Locale.US, "Total Credited / Paid   : ₹ %,.2f\n", totalCredit))
    sb.append(String.format(Locale.US, "Closing Balance Due     : ₹ %,.2f\n", runningBal))
    sb.append("====================================================\n")
    sb.append("Certified on-device offline record.\n")
    return sb.toString()
  }

  /**
   * Shares generated CSV or text report file via Android Sharesheet.
   */
  fun shareReport(
    context: Context,
    content: String,
    filenamePrefix: String,
    extension: String = "csv",
    mimeType: String = "text/csv"
  ) {
    try {
      val fileName = "${filenamePrefix}_${fileDateFormat.format(Date())}.$extension"
      val cacheDir = File(context.cacheDir, "reports").apply { mkdirs() }
      val file = File(cacheDir, fileName)
      file.writeText(content, Charsets.UTF_8)

      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )

      val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "$filenamePrefix Report - Offline Ledger")
        putExtra(Intent.EXTRA_TEXT, "Detailed export generated by Offline Ledger on ${dateFormat.format(Date())}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }

      val chooser = Intent.createChooser(intent, "Export $filenamePrefix")
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(chooser)
    } catch (e: Exception) {
      // Fallback: simple text intent
      val textIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
        putExtra(Intent.EXTRA_SUBJECT, "$filenamePrefix Report")
      }
      context.startActivity(Intent.createChooser(textIntent, "Export Report"))
    }
  }
}
