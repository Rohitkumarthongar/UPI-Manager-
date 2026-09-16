package com.example.sync

import com.example.data.model.TransactionItem
import com.example.data.model.UpiAccount
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SyncTransactionDto(
  val syncId: String,
  val amount: Double,
  val type: String, // "INCOME", "EXPENSE"
  val category: String,
  val upiId: String?, // Primary key for matching across devices (e.g. "store@okhdfcbank")
  val accountLabel: String?,
  val note: String,
  val vendorName: String?,
  val referenceNumber: String?,
  val source: String,
  val timestamp: Long,
  val originDevice: String? = null
)

data class SyncUpiAccountDto(
  val upiId: String,
  val payeeName: String,
  val label: String,
  val bankName: String,
  val monthlyLimit: Double,
  val quarterlyLimit: Double,
  val isLimitEnforced: Boolean
)

data class SyncPayload(
  val syncCode: String,
  val deviceName: String,
  val deviceId: String,
  val timestamp: Long,
  val transactions: List<SyncTransactionDto>,
  val accounts: List<SyncUpiAccountDto>
) {
  fun toJson(): String {
    val json = JSONObject()
    json.put("syncCode", syncCode)
    json.put("deviceName", deviceName)
    json.put("deviceId", deviceId)
    json.put("timestamp", timestamp)

    val txnsArray = JSONArray()
    for (t in transactions) {
      val tObj = JSONObject().apply {
        put("syncId", t.syncId)
        put("amount", t.amount)
        put("type", t.type)
        put("category", t.category)
        put("upiId", t.upiId ?: "")
        put("accountLabel", t.accountLabel ?: "")
        put("note", t.note)
        put("vendorName", t.vendorName ?: "")
        put("referenceNumber", t.referenceNumber ?: "")
        put("source", t.source)
        put("timestamp", t.timestamp)
        put("originDevice", t.originDevice ?: "")
      }
      txnsArray.put(tObj)
    }
    json.put("transactions", txnsArray)

    val accsArray = JSONArray()
    for (a in accounts) {
      val aObj = JSONObject().apply {
        put("upiId", a.upiId)
        put("payeeName", a.payeeName)
        put("label", a.label)
        put("bankName", a.bankName)
        put("monthlyLimit", a.monthlyLimit)
        put("quarterlyLimit", a.quarterlyLimit)
        put("isLimitEnforced", a.isLimitEnforced)
      }
      accsArray.put(aObj)
    }
    json.put("accounts", accsArray)

    return json.toString()
  }

  companion object {
    fun fromJson(jsonStr: String): SyncPayload {
      val json = JSONObject(jsonStr)
      val syncCode = json.optString("syncCode", "")
      val deviceName = json.optString("deviceName", "Unknown Device")
      val deviceId = json.optString("deviceId", UUID.randomUUID().toString())
      val timestamp = json.optLong("timestamp", System.currentTimeMillis())

      val txnsList = mutableListOf<SyncTransactionDto>()
      val txnsArray = json.optJSONArray("transactions") ?: JSONArray()
      for (i in 0 until txnsArray.length()) {
        val o = txnsArray.getJSONObject(i)
        txnsList.add(
          SyncTransactionDto(
            syncId = o.optString("syncId", UUID.randomUUID().toString()),
            amount = o.optDouble("amount", 0.0),
            type = o.optString("type", "EXPENSE"),
            category = o.optString("category", "General"),
            upiId = o.optString("upiId").ifBlank { null },
            accountLabel = o.optString("accountLabel").ifBlank { null },
            note = o.optString("note", ""),
            vendorName = o.optString("vendorName").ifBlank { null },
            referenceNumber = o.optString("referenceNumber").ifBlank { null },
            source = o.optString("source", "SYNCED"),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
            originDevice = o.optString("originDevice").ifBlank { null }
          )
        )
      }

      val accsList = mutableListOf<SyncUpiAccountDto>()
      val accsArray = json.optJSONArray("accounts") ?: JSONArray()
      for (i in 0 until accsArray.length()) {
        val a = accsArray.getJSONObject(i)
        accsList.add(
          SyncUpiAccountDto(
            upiId = a.optString("upiId", ""),
            payeeName = a.optString("payeeName", ""),
            label = a.optString("label", "UPI Account"),
            bankName = a.optString("bankName", ""),
            monthlyLimit = a.optDouble("monthlyLimit", 0.0),
            quarterlyLimit = a.optDouble("quarterlyLimit", 0.0),
            isLimitEnforced = a.optBoolean("isLimitEnforced", true)
          )
        )
      }

      return SyncPayload(syncCode, deviceName, deviceId, timestamp, txnsList, accsList)
    }
  }
}

data class SyncResponse(
  val success: Boolean,
  val message: String,
  val hostDeviceName: String,
  val syncTimestamp: Long,
  val transactions: List<SyncTransactionDto>,
  val accounts: List<SyncUpiAccountDto>
) {
  fun toJson(): String {
    val json = JSONObject()
    json.put("success", success)
    json.put("message", message)
    json.put("hostDeviceName", hostDeviceName)
    json.put("syncTimestamp", syncTimestamp)

    val txnsArray = JSONArray()
    for (t in transactions) {
      val tObj = JSONObject().apply {
        put("syncId", t.syncId)
        put("amount", t.amount)
        put("type", t.type)
        put("category", t.category)
        put("upiId", t.upiId ?: "")
        put("accountLabel", t.accountLabel ?: "")
        put("note", t.note)
        put("vendorName", t.vendorName ?: "")
        put("referenceNumber", t.referenceNumber ?: "")
        put("source", t.source)
        put("timestamp", t.timestamp)
        put("originDevice", t.originDevice ?: "")
      }
      txnsArray.put(tObj)
    }
    json.put("transactions", txnsArray)

    val accsArray = JSONArray()
    for (a in accounts) {
      val aObj = JSONObject().apply {
        put("upiId", a.upiId)
        put("payeeName", a.payeeName)
        put("label", a.label)
        put("bankName", a.bankName)
        put("monthlyLimit", a.monthlyLimit)
        put("quarterlyLimit", a.quarterlyLimit)
        put("isLimitEnforced", a.isLimitEnforced)
      }
      accsArray.put(aObj)
    }
    json.put("accounts", accsArray)

    return json.toString()
  }

  companion object {
    fun fromJson(jsonStr: String): SyncResponse {
      val json = JSONObject(jsonStr)
      val success = json.optBoolean("success", false)
      val message = json.optString("message", "")
      val hostDeviceName = json.optString("hostDeviceName", "Host Device")
      val syncTimestamp = json.optLong("syncTimestamp", System.currentTimeMillis())

      val txnsList = mutableListOf<SyncTransactionDto>()
      val txnsArray = json.optJSONArray("transactions") ?: JSONArray()
      for (i in 0 until txnsArray.length()) {
        val o = txnsArray.getJSONObject(i)
        txnsList.add(
          SyncTransactionDto(
            syncId = o.optString("syncId", UUID.randomUUID().toString()),
            amount = o.optDouble("amount", 0.0),
            type = o.optString("type", "EXPENSE"),
            category = o.optString("category", "General"),
            upiId = o.optString("upiId").ifBlank { null },
            accountLabel = o.optString("accountLabel").ifBlank { null },
            note = o.optString("note", ""),
            vendorName = o.optString("vendorName").ifBlank { null },
            referenceNumber = o.optString("referenceNumber").ifBlank { null },
            source = o.optString("source", "SYNCED"),
            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
            originDevice = o.optString("originDevice").ifBlank { null }
          )
        )
      }

      val accsList = mutableListOf<SyncUpiAccountDto>()
      val accsArray = json.optJSONArray("accounts") ?: JSONArray()
      for (i in 0 until accsArray.length()) {
        val a = accsArray.getJSONObject(i)
        accsList.add(
          SyncUpiAccountDto(
            upiId = a.optString("upiId", ""),
            payeeName = a.optString("payeeName", ""),
            label = a.optString("label", "UPI Account"),
            bankName = a.optString("bankName", ""),
            monthlyLimit = a.optDouble("monthlyLimit", 0.0),
            quarterlyLimit = a.optDouble("quarterlyLimit", 0.0),
            isLimitEnforced = a.optBoolean("isLimitEnforced", true)
          )
        )
      }

      return SyncResponse(success, message, hostDeviceName, syncTimestamp, txnsList, accsList)
    }
  }
}

/**
 * Parses connection string from QR code or manual input:
 * Formats:
 * 1. "http://192.168.1.45:8888?code=123456"
 * 2. "OFFLINE_SYNC://192.168.1.45:8888?code=123456"
 * 3. "192.168.1.45:8888"
 */
data class ParsedSyncConfig(
  val hostUrl: String,
  val syncCode: String?
) {
  companion object {
    fun parse(raw: String): ParsedSyncConfig? {
      val trimmed = raw.trim()
      if (trimmed.isBlank()) return null

      var url = trimmed
      var code: String? = null

      if (url.startsWith("OFFLINE_SYNC://", ignoreCase = true)) {
        url = "http://" + url.substring("OFFLINE_SYNC://".length)
      } else if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "http://$url"
      }

      if (url.contains("?")) {
        val parts = url.split("?", limit = 2)
        url = parts[0]
        val query = parts[1]
        val params = query.split("&")
        for (p in params) {
          val kv = p.split("=", limit = 2)
          if (kv.size == 2 && kv[0].equals("code", ignoreCase = true)) {
            code = kv[1]
          }
        }
      }

      return ParsedSyncConfig(hostUrl = url, syncCode = code)
    }
  }
}
