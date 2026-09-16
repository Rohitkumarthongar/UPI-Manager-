package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sync.ConnectedClientInfo
import com.example.sync.LocalSyncServer
import com.example.sync.ParsedSyncConfig
import com.example.ui.components.FintechCard
import com.example.ui.components.StatusTag
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentRose
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.util.QrGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiDeviceSyncScreen(
  isHosting: Boolean,
  serverIp: String?,
  serverPort: Int,
  serverSyncCode: String,
  clientHostUrl: String,
  clientSyncCode: String,
  customDeviceName: String = "Mobile 1",
  connectedClients: List<ConnectedClientInfo> = emptyList(),
  isSyncing: Boolean,
  isAutoSyncEnabled: Boolean,
  lastSyncSummary: String,
  lastSyncTime: Long?,
  syncLogs: List<String>,
  onStartServer: () -> Unit,
  onStopServer: () -> Unit,
  onRegenerateCode: () -> Unit,
  onUpdateClientUrl: (String) -> Unit,
  onUpdateClientCode: (String) -> Unit,
  onUpdateCustomDeviceName: (String) -> Unit = {},
  onSyncNow: (String, String, (Boolean, String) -> Unit) -> Unit,
  onTestPing: (String, (Boolean, String) -> Unit) -> Unit,
  onToggleAutoSync: (Boolean) -> Unit,
  onSimulateReceivedPayment: ((Double, String) -> Unit)? = null,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  var selectedTab by remember { mutableIntStateOf(if (isHosting) 0 else 1) }

  // Photo/QR picker launcher
  val qrPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
          @Suppress("DEPRECATION")
          MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val decodedText = QrGenerator.decodeQrFromBitmap(bitmap)
        if (!decodedText.isNullOrBlank()) {
          val parsed = ParsedSyncConfig.parse(decodedText)
          if (parsed != null) {
            onUpdateClientUrl(parsed.hostUrl)
            if (!parsed.syncCode.isNullOrBlank()) {
              onUpdateClientCode(parsed.syncCode)
            }
            Toast.makeText(context, "Loaded Sync Hub config from QR code!", Toast.LENGTH_SHORT).show()
          } else {
            onUpdateClientUrl(decodedText)
            Toast.makeText(context, "Loaded QR text: $decodedText", Toast.LENGTH_SHORT).show()
          }
        } else {
          Toast.makeText(context, "Could not detect a QR code in selected image", Toast.LENGTH_SHORT).show()
        }
      } catch (e: Exception) {
        Toast.makeText(context, "Failed to read QR image: ${e.message}", Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Camera QR scanner launcher
  val takePhotoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap: Bitmap? ->
    if (bitmap != null) {
      val decoded = QrGenerator.decodeQrFromBitmap(bitmap)
      if (!decoded.isNullOrBlank()) {
        val parsed = ParsedSyncConfig.parse(decoded)
        if (parsed != null) {
          onUpdateClientUrl(parsed.hostUrl)
          if (!parsed.syncCode.isNullOrBlank()) {
            onUpdateClientCode(parsed.syncCode)
          }
          Toast.makeText(context, "Scanned Sync Hub QR successfully!", Toast.LENGTH_SHORT).show()
        } else {
          onUpdateClientUrl(decoded)
          Toast.makeText(context, "Scanned: $decoded", Toast.LENGTH_SHORT).show()
        }
      } else {
        Toast.makeText(context, "No QR code detected. Try holding steady or picking an image.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "Multi-Device Range Sync",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Offline UPI & Quota Synchronization",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          StatusTag(
            text = if (isHosting) "Hub Online" else if (isAutoSyncEnabled) "Auto-Syncing" else "Ready",
            color = if (isHosting || isAutoSyncEnabled) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      val isWideScreen = maxWidth >= 680.dp

      if (isWideScreen) {
        // Dual-Pane Responsive Layout for Tablets/Expanded screens
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
        ) {
          OperatorIdentityCard(
            deviceName = customDeviceName,
            onUpdateDeviceName = onUpdateCustomDeviceName
          )

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            // Left Pane: Host Server Hub
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
              modifier = Modifier
                .weight(1f)
                .fillMaxSize()
            ) {
              Column(
                modifier = Modifier
                  .padding(16.dp)
                  .verticalScroll(rememberScrollState())
              ) {
                HostHubPanel(
                  isHosting = isHosting,
                  serverIp = serverIp,
                  serverPort = serverPort,
                  serverSyncCode = serverSyncCode,
                  connectedClients = connectedClients,
                  lastSyncSummary = lastSyncSummary,
                  lastSyncTime = lastSyncTime,
                  onStartServer = onStartServer,
                  onStopServer = onStopServer,
                  onRegenerateCode = onRegenerateCode
                )
              }
            }

            // Right Pane: Client Connect & Logs
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
              modifier = Modifier
                .weight(1f)
                .fillMaxSize()
            ) {
              Column(
                modifier = Modifier
                  .padding(16.dp)
                  .verticalScroll(rememberScrollState())
              ) {
                ClientConnectPanel(
                  clientHostUrl = clientHostUrl,
                  clientSyncCode = clientSyncCode,
                  isSyncing = isSyncing,
                  isAutoSyncEnabled = isAutoSyncEnabled,
                  lastSyncSummary = lastSyncSummary,
                  onUpdateClientUrl = onUpdateClientUrl,
                  onUpdateClientCode = onUpdateClientCode,
                  onSyncNow = onSyncNow,
                  onTestPing = onTestPing,
                  onToggleAutoSync = onToggleAutoSync,
                  onSimulateReceivedPayment = onSimulateReceivedPayment,
                  onScanCamera = { takePhotoLauncher.launch(null) },
                  onPickImage = { qrPickerLauncher.launch("image/*") }
                )

                Spacer(modifier = Modifier.height(16.dp))
                SyncLogsCard(syncLogs = syncLogs)
              }
            }
          }
        }
      } else {
        // Single Column Layout with Tabs for Handheld Phones
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
        ) {
          Spacer(modifier = Modifier.height(8.dp))

          OperatorIdentityCard(
            deviceName = customDeviceName,
            onUpdateDeviceName = onUpdateCustomDeviceName
          )

          Spacer(modifier = Modifier.height(10.dp))

          TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
          ) {
            Tab(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Host Hub Mode")
                }
              }
            )
            Tab(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Client Connect")
                }
              }
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
          ) {
            if (selectedTab == 0) {
              HostHubPanel(
                isHosting = isHosting,
                serverIp = serverIp,
                serverPort = serverPort,
                serverSyncCode = serverSyncCode,
                connectedClients = connectedClients,
                lastSyncSummary = lastSyncSummary,
                lastSyncTime = lastSyncTime,
                onStartServer = onStartServer,
                onStopServer = onStopServer,
                onRegenerateCode = onRegenerateCode
              )
            } else {
              ClientConnectPanel(
                clientHostUrl = clientHostUrl,
                clientSyncCode = clientSyncCode,
                isSyncing = isSyncing,
                isAutoSyncEnabled = isAutoSyncEnabled,
                lastSyncSummary = lastSyncSummary,
                onUpdateClientUrl = onUpdateClientUrl,
                onUpdateClientCode = onUpdateClientCode,
                onSyncNow = onSyncNow,
                onTestPing = onTestPing,
                onToggleAutoSync = onToggleAutoSync,
                onSimulateReceivedPayment = onSimulateReceivedPayment,
                onScanCamera = { takePhotoLauncher.launch(null) },
                onPickImage = { qrPickerLauncher.launch("image/*") }
              )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SyncLogsCard(syncLogs = syncLogs)
            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      }
    }
  }
}

@Composable
fun HostHubPanel(
  isHosting: Boolean,
  serverIp: String?,
  serverPort: Int,
  serverSyncCode: String,
  connectedClients: List<ConnectedClientInfo> = emptyList(),
  lastSyncSummary: String,
  lastSyncTime: Long?,
  onStartServer: () -> Unit,
  onStopServer: () -> Unit,
  onRegenerateCode: () -> Unit
) {
  val context = LocalContext.current
  val activeIp = serverIp ?: LocalSyncServer.getLocalIpAddress() ?: "192.168.43.1"
  val fullHostUrl = "http://$activeIp:$serverPort"
  val qrString = "$fullHostUrl?code=$serverSyncCode"

  val qrBitmap = remember(qrString, isHosting) {
    if (isHosting) QrGenerator.generateQrBitmap(qrString, sizePx = 400) else null
  }

  FintechCard(modifier = Modifier.fillMaxWidth()) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(if (isHosting) EmeraldPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.WifiTethering,
              contentDescription = null,
              tint = if (isHosting) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "This Phone as Sync Hub",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = if (isHosting) "Broadcasting within range" else "Offline server stopped",
              style = MaterialTheme.typography.labelSmall,
              color = if (isHosting) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        StatusTag(
          text = if (isHosting) "LIVE HUB" else "STOPPED",
          color = if (isHosting) EmeraldDark else AccentRose
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Server toggle button
      if (!isHosting) {
        Button(
          onClick = onStartServer,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("start_hub_button")
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Start Sync Hub", fontWeight = FontWeight.Bold)
        }
      } else {
        OutlinedButton(
          onClick = onStopServer,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("stop_hub_button")
        ) {
          Icon(Icons.Default.Stop, contentDescription = null, tint = AccentRose)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Stop Sync Hub", color = AccentRose, fontWeight = FontWeight.Bold)
        }
      }

      AnimatedVisibility(visible = isHosting) {
        Column {
          Spacer(modifier = Modifier.height(16.dp))

          // Connection URL Card
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Hub Network URL",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = fullHostUrl,
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                  ),
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
              IconButton(
                onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("Sync URL", fullHostUrl))
                  Toast.makeText(context, "Copied URL to clipboard", Toast.LENGTH_SHORT).show()
                }
              ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // 6-Digit Pairing Code Card
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "6-Digit Sync Code",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = serverSyncCode,
                  style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                  ),
                  color = EmeraldDark
                )
              }
              IconButton(onClick = onRegenerateCode) {
                Icon(Icons.Default.Refresh, contentDescription = "Regenerate code")
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Connected Operator Phones Status
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Default.Devices,
                    contentDescription = null,
                    tint = EmeraldDark,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Connected Mobiles (${connectedClients.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  )
                }

                if (connectedClients.isNotEmpty()) {
                  StatusTag(text = "Online", color = EmeraldDark)
                }
              }

              Spacer(modifier = Modifier.height(6.dp))

              if (connectedClients.isEmpty()) {
                Text(
                  text = "No secondary mobile synced yet. Once Mobile 1 or Mobile 2 syncs over Wi-Fi, it will appear here.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              } else {
                connectedClients.forEach { client ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Text(
                        text = client.deviceName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                      )
                      Text(
                        text = "${client.ipAddress} • ${client.txnsCount} txns received",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    StatusTag(text = "Synced", color = EmeraldDark)
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // QR Code for 1-Tap Client Pairing
          if (qrBitmap != null) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "Instant Pairing QR Code",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Scan from secondary phone camera to sync immediately",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(10.dp))
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(4.dp)
              ) {
                Image(
                  bitmap = qrBitmap.asImageBitmap(),
                  contentDescription = "Sync Pairing QR Code",
                  modifier = Modifier
                    .size(180.dp)
                    .padding(8.dp)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Helper Tips
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
          Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Ensure other phones are on the same Wi-Fi router or connected to this phone's Personal Hotspot.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }
  }
}

@Composable
fun ClientConnectPanel(
  clientHostUrl: String,
  clientSyncCode: String,
  isSyncing: Boolean,
  isAutoSyncEnabled: Boolean,
  lastSyncSummary: String,
  onUpdateClientUrl: (String) -> Unit,
  onUpdateClientCode: (String) -> Unit,
  onSyncNow: (String, String, (Boolean, String) -> Unit) -> Unit,
  onTestPing: (String, (Boolean, String) -> Unit) -> Unit,
  onToggleAutoSync: (Boolean) -> Unit,
  onSimulateReceivedPayment: ((Double, String) -> Unit)? = null,
  onScanCamera: () -> Unit,
  onPickImage: () -> Unit
) {
  val context = LocalContext.current
  var testResult by remember { mutableStateOf<String?>(null) }
  var isTesting by remember { mutableStateOf(false) }

  FintechCard(modifier = Modifier.fillMaxWidth()) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(AccentCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.Link,
              contentDescription = null,
              tint = AccentCyan,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Connect to Host Phone",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Push & pull transactions with same UPI",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        StatusTag(
          text = if (isAutoSyncEnabled) "Auto-Sync ON" else "Manual",
          color = if (isAutoSyncEnabled) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Host URL Input
      OutlinedTextField(
        value = clientHostUrl,
        onValueChange = onUpdateClientUrl,
        label = { Text("Host Hub URL or IP Address") },
        placeholder = { Text("e.g. 192.168.43.1:8888") },
        leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null) },
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("host_url_input")
      )

      Spacer(modifier = Modifier.height(6.dp))

      // Quick Subnet Presets
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = clientHostUrl.contains("192.168.43.1"),
          onClick = { onUpdateClientUrl("http://192.168.43.1:8888") },
          label = { Text("Hotspot Default (43.1)") }
        )
        FilterChip(
          selected = false,
          onClick = {
            val ip = LocalSyncServer.getLocalIpAddress()
            if (ip != null) {
              val prefix = ip.substringBeforeLast(".")
              onUpdateClientUrl("http://$prefix.1:8888")
            }
          },
          label = { Text("Local Subnet (.1)") }
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Sync Code Input
      OutlinedTextField(
        value = clientSyncCode,
        onValueChange = onUpdateClientCode,
        label = { Text("6-Digit Sync Pairing Code") },
        placeholder = { Text("e.g. 849201") },
        leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sync_code_input")
      )

      Spacer(modifier = Modifier.height(12.dp))

      // QR Scanner Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onScanCamera,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .testTag("scan_qr_camera_button")
        ) {
          Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Camera QR", style = MaterialTheme.typography.labelSmall)
        }

        OutlinedButton(
          onClick = onPickImage,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .testTag("pick_qr_image_button")
        ) {
          Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Pick QR Image", style = MaterialTheme.typography.labelSmall)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Test Ping Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = {
            if (clientHostUrl.isBlank()) {
              Toast.makeText(context, "Enter Host URL first", Toast.LENGTH_SHORT).show()
              return@OutlinedButton
            }
            isTesting = true
            onTestPing(clientHostUrl) { ok, msg ->
              isTesting = false
              testResult = msg
              Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
          },
          enabled = !isTesting && !isSyncing,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).height(44.dp)
        ) {
          if (isTesting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Test Ping")
          }
        }

        // Primary Sync Button
        Button(
          onClick = {
            onSyncNow(clientHostUrl, clientSyncCode) { success, msg ->
              Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
          },
          enabled = !isSyncing && clientHostUrl.isNotBlank() && clientSyncCode.isNotBlank(),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
          modifier = Modifier
            .weight(1.4f)
            .height(44.dp)
            .testTag("sync_now_button")
        ) {
          if (isSyncing) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = androidx.compose.ui.graphics.Color.White,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Syncing...")
          } else {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sync Now", fontWeight = FontWeight.Bold)
          }
        }
      }

      if (testResult != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = testResult ?: "",
          style = MaterialTheme.typography.labelSmall,
          color = if (testResult?.contains("success", ignoreCase = true) == true) EmeraldDark else AccentRose
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Auto-Sync Switch
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Auto-Sync within Range",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Synchronizes every 20s while devices are connected",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = isAutoSyncEnabled,
            onCheckedChange = onToggleAutoSync
          )
        }
      }

      if (onSimulateReceivedPayment != null) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
          onClick = {
            val randomAmt = listOf(250.0, 500.0, 750.0, 1200.0, 1850.0).random()
            onSimulateReceivedPayment(randomAmt, "Customer via UPI")
            Toast.makeText(context, "Simulated ₹${randomAmt.toInt()} payment on this mobile!", Toast.LENGTH_SHORT).show()
          },
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .testTag("simulate_payment_button")
        ) {
          Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Simulate Payment Received on this Mobile", style = MaterialTheme.typography.labelSmall)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Last Sync Status
      Text(
        text = "Last Sync Status: $lastSyncSummary",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun OperatorIdentityCard(
  deviceName: String,
  onUpdateDeviceName: (String) -> Unit
) {
  var showWorkflowInfo by remember { mutableStateOf(false) }

  FintechCard(modifier = Modifier.fillMaxWidth()) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(34.dp)
              .clip(CircleShape)
              .background(EmeraldPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.PhoneAndroid,
              contentDescription = null,
              tint = EmeraldDark,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "This Mobile's Identity",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Device tag shown on synced transactions",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        IconButton(onClick = { showWorkflowInfo = !showWorkflowInfo }) {
          Icon(
            Icons.Default.HelpOutline,
            contentDescription = "How 3-Mobile Sync Works",
            tint = MaterialTheme.colorScheme.primary
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedTextField(
        value = deviceName,
        onValueChange = onUpdateDeviceName,
        label = { Text("Operator Phone Label") },
        placeholder = { Text("e.g. Mobile 1, Mobile 2, Cash Counter") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Mobile 1", "Mobile 2", "Mobile 3 (Host)").forEach { preset ->
          FilterChip(
            selected = deviceName.equals(preset, ignoreCase = true),
            onClick = { onUpdateDeviceName(preset) },
            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
          )
        }
      }

      AnimatedVisibility(visible = showWorkflowInfo) {
        Column(
          modifier = Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(12.dp)
        ) {
          Text(
            text = "3-Mobile Operator Sync Workflow:",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "1. Connect Mobile 1, Mobile 2, and Mobile 3 to the same Wi-Fi router or Hotspot.\n" +
              "2. Set Mobile 3 as 'Sync Hub' (tap Start Sync Hub).\n" +
              "3. On Mobile 1 & Mobile 2, enter the Hub URL (or scan QR) and turn on Auto-Sync.\n" +
              "4. When payments are received on Mobile 1 or Mobile 2 via UPI, they automatically merge into Mobile 3 with source tags and intelligent duplicate prevention.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }
  }
}

@Composable
fun SyncLogsCard(syncLogs: List<String>) {
  FintechCard(modifier = Modifier.fillMaxWidth()) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Sync Activity & Network Logs",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
          text = "${syncLogs.size} events",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      if (syncLogs.isEmpty()) {
        Text(
          text = "No sync activity recorded yet. Start Hub or tap Sync Now.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          syncLogs.take(8).forEach { log ->
            Text(
              text = log,
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
              color = if (log.contains("failed", ignoreCase = true) || log.contains("error", ignoreCase = true)) {
                AccentRose
              } else if (log.contains("success", ignoreCase = true) || log.contains("Merged", ignoreCase = true)) {
                EmeraldDark
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              }
            )
          }
        }
      }
    }
  }
}
