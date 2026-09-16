package com.example.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LocalSyncClient {
  private val client = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  /**
   * Pings the host URL to verify connectivity and sync hub availability.
   */
  suspend fun ping(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val cleanUrl = sanitizeUrl(rawUrl) + "/status"
      val request = Request.Builder()
        .url(cleanUrl)
        .get()
        .build()

      val response = client.newCall(request).execute()
      response.use { res ->
        if (res.isSuccessful) {
          Result.success("Connected to Sync Hub successfully (${res.code})")
        } else {
          Result.failure(Exception("Host returned HTTP ${res.code}: ${res.message}"))
        }
      }
    } catch (e: Exception) {
      Result.failure(Exception("Cannot reach host: ${e.localizedMessage ?: "Connection timed out"}. Ensure both devices are on same Wi-Fi/Hotspot."))
    }
  }

  /**
   * Sends local sync payload and receives host's transactions & UPI accounts.
   */
  suspend fun sync(
    rawUrl: String,
    payload: SyncPayload
  ): Result<SyncResponse> = withContext(Dispatchers.IO) {
    try {
      val cleanUrl = sanitizeUrl(rawUrl) + "/sync"
      val requestBody = payload.toJson().toRequestBody(jsonMediaType)

      val request = Request.Builder()
        .url(cleanUrl)
        .post(requestBody)
        .addHeader("X-Sync-Code", payload.syncCode)
        .build()

      val response = client.newCall(request).execute()
      response.use { res ->
        val bodyStr = res.body?.string() ?: ""
        if (res.isSuccessful) {
          try {
            val syncResponse = SyncResponse.fromJson(bodyStr)
            Result.success(syncResponse)
          } catch (e: Exception) {
            Result.failure(Exception("Failed to parse sync response: ${e.message}"))
          }
        } else if (res.code == 403) {
          Result.failure(Exception("Sync Code rejected! Check that both phones have the exact same 6-digit code."))
        } else {
          Result.failure(Exception("Host returned error ${res.code}: $bodyStr"))
        }
      }
    } catch (e: Exception) {
      Result.failure(Exception("Sync failed: ${e.localizedMessage ?: "Network error"}. Check IP address and Wi-Fi range."))
    }
  }

  private fun sanitizeUrl(input: String): String {
    var trimmed = input.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
      trimmed = "http://$trimmed"
    }
    if (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length - 1)
    }
    return trimmed
  }
}
