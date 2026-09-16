package com.example.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

data class ConnectedClientInfo(
  val deviceName: String,
  val deviceId: String,
  val ipAddress: String,
  val lastSyncTime: Long,
  val txnsCount: Int
)

class LocalSyncServer(
  private val scope: CoroutineScope
) {
  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null
  private val _connectedClients = java.util.concurrent.ConcurrentHashMap<String, ConnectedClientInfo>()

  var isRunning: Boolean = false
    private set

  var activePort: Int = 8888
    private set

  var syncCode: String = "123456"
    private set

  var connectedClientsCount: Int = 0
    private set

  var lastSyncTime: Long? = null
    private set

  var lastSyncSummary: String = "No sync yet"
    private set

  fun getConnectedClients(): List<ConnectedClientInfo> {
    return _connectedClients.values.sortedByDescending { it.lastSyncTime }
  }

  /**
   * Starts listening for client phones on the local network range (Wi-Fi or Hotspot).
   */
  fun start(
    port: Int = 8888,
    code: String,
    onSyncReceived: suspend (SyncPayload) -> SyncResponse
  ) {
    if (isRunning) return
    activePort = port
    syncCode = code
    isRunning = true

    serverJob = scope.launch(Dispatchers.IO) {
      try {
        val server = ServerSocket(port)
        serverSocket = server

        while (isActive && !server.isClosed) {
          try {
            val socket = server.accept()
            launch(Dispatchers.IO) {
              handleClientSocket(socket, onSyncReceived)
            }
          } catch (e: Exception) {
            if (!isActive || server.isClosed) break
          }
        }
      } catch (e: Exception) {
        isRunning = false
      }
    }
  }

  fun stop() {
    isRunning = false
    try {
      serverSocket?.close()
    } catch (_: Exception) {}
    serverSocket = null
    serverJob?.cancel()
    serverJob = null
    _connectedClients.clear()
  }

  private suspend fun handleClientSocket(
    socket: Socket,
    onSyncReceived: suspend (SyncPayload) -> SyncResponse
  ) {
    socket.use { client ->
      try {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
        val output: OutputStream = client.getOutputStream()

        // Read HTTP request line
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return

        val method = parts[0]
        val path = parts[1]

        var contentLength = 0
        var headerSyncCode: String? = null
        var line: String? = reader.readLine()
        while (!line.isNullOrBlank()) {
          val lower = line.lowercase()
          if (lower.startsWith("content-length:")) {
            contentLength = line.substring("content-length:".length).trim().toIntOrNull() ?: 0
          } else if (lower.startsWith("x-sync-code:")) {
            headerSyncCode = line.substring("x-sync-code:".length).trim()
          }
          line = reader.readLine()
        }

        when {
          path.startsWith("/status") && method == "GET" -> {
            val statusJson = "{\"status\":\"OK\",\"host\":\"OfflineSyncHub\",\"syncCodeMatch\":true}"
            writeResponse(output, 200, "OK", statusJson)
          }

          path.startsWith("/sync") && method == "POST" -> {
            // Read body
            val bodyChars = CharArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength) {
              val count = reader.read(bodyChars, readTotal, contentLength - readTotal)
              if (count == -1) break
              readTotal += count
            }
            val bodyString = String(bodyChars, 0, readTotal)

            val payload = try {
              SyncPayload.fromJson(bodyString)
            } catch (e: Exception) {
              null
            }

            if (payload == null) {
              writeResponse(output, 400, "Bad Request", "{\"error\":\"Invalid JSON payload\"}")
              return
            }

            // Verify pairing code
            val clientCode = if (payload.syncCode.isNotBlank()) payload.syncCode else headerSyncCode
            if (clientCode != syncCode) {
              val err = "{\"success\":false,\"error\":\"Invalid sync pairing code. Ensure code matches Host.\"}"
              writeResponse(output, 403, "Forbidden", err)
              return
            }

            // Process sync
            val response = onSyncReceived(payload)
            connectedClientsCount++
            lastSyncTime = System.currentTimeMillis()
            lastSyncSummary = "Synced with ${payload.deviceName} (${payload.transactions.size} txns)"

            val clientIp = client.inetAddress?.hostAddress ?: "Unknown"
            _connectedClients[payload.deviceId] = ConnectedClientInfo(
              deviceName = payload.deviceName,
              deviceId = payload.deviceId,
              ipAddress = clientIp,
              lastSyncTime = System.currentTimeMillis(),
              txnsCount = payload.transactions.size
            )

            writeResponse(output, 200, "OK", response.toJson())
          }

          else -> {
            writeResponse(output, 404, "Not Found", "{\"error\":\"Not Found\"}")
          }
        }
      } catch (e: Exception) {
        // Socket closed or connection reset
      }
    }
  }

  private fun writeResponse(output: OutputStream, statusCode: Int, statusText: String, jsonBody: String) {
    val bodyBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
    val header = "HTTP/1.1 $statusCode $statusText\r\n" +
      "Content-Type: application/json; charset=utf-8\r\n" +
      "Content-Length: ${bodyBytes.size}\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Connection: close\r\n\r\n"
    output.write(header.toByteArray(StandardCharsets.UTF_8))
    output.write(bodyBytes)
    output.flush()
  }

  companion object {
    /**
     * Resolves the device's local IPv4 address within the local network (Wi-Fi or Hotspot).
     */
    fun getLocalIpAddress(): String? {
      try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
          val intf = interfaces.nextElement()
          val addrs = intf.inetAddresses
          while (addrs.hasMoreElements()) {
            val addr = addrs.nextElement()
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
              val host = addr.hostAddress
              if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                return host
              }
            }
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
      return null
    }
  }
}
