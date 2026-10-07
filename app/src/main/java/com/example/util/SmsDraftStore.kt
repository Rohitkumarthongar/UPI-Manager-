package com.example.util

import android.content.Context
import com.example.data.model.NotificationDraft
import org.json.JSONArray
import org.json.JSONObject

/** App-private inbox and decision history. Never stores data in an external service. */
class SmsDraftStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("sms_payment_drafts", Context.MODE_PRIVATE)
  companion object { private val lock = Any() }

  fun pending(): List<NotificationDraft> = synchronized(lock) { readPending() }

  fun add(draft: NotificationDraft): Boolean = synchronized(lock) {
    val decided = prefs.getStringSet("decided", emptySet()).orEmpty()
    if (draft.id in decided) return@synchronized false
    val drafts = readPending()
    if (drafts.any { it.id == draft.id }) return@synchronized false
    drafts.add(draft)
    writePending(drafts)
    true
  }

  fun decide(id: String) = synchronized(lock) {
    val drafts = readPending().filterNot { it.id == id }
    // Commit the decision before the pending list so a crash cannot resurrect this payment.
    prefs.edit().putStringSet("decided", prefs.getStringSet("decided", emptySet()).orEmpty() + id).commit()
    writePending(drafts)
  }

  private fun readPending(): MutableList<NotificationDraft> {
    val array = runCatching { JSONArray(prefs.getString("pending", "[]")) }.getOrElse { JSONArray() }
    val decided = prefs.getStringSet("decided", emptySet()).orEmpty()
    return (0 until array.length()).mapNotNull { index ->
      runCatching {
        val item = array.getJSONObject(index)
        NotificationDraft(
          id = item.getString("id"), senderApp = item.getString("sender"),
          amount = item.getDouble("amount"), type = item.getString("type"),
          rawText = item.getString("body"), senderOrReceiver = item.getString("party"),
          timestamp = item.getLong("date"),
          upiReference = item.optString("reference").takeIf { it.isNotBlank() }
        )
      }.getOrNull()
    }.filterNot { it.id in decided }.toMutableList()
  }

  private fun writePending(drafts: List<NotificationDraft>) {
    val array = JSONArray()
    drafts.forEach { draft ->
      array.put(JSONObject().apply {
        put("id", draft.id); put("sender", draft.senderApp); put("amount", draft.amount)
        put("type", draft.type); put("body", draft.rawText); put("party", draft.senderOrReceiver)
        put("date", draft.timestamp); put("reference", draft.upiReference)
      })
    }
    prefs.edit().putString("pending", array.toString()).commit()
  }
}
