@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

import adb.AdbController
import adb.Device
import adb.DeviceStore
import adb.ScrcpyController
import adb.SessionStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.launch
import ui.*

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(DeviceStore.load()) }
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var status by remember { mutableStateOf(ConnectionStatus.DISCONNECTED) }
    var errorMessage by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newIp by remember { mutableStateOf("") }
    var newPort by remember { mutableStateOf("5555") }
    var unlockStatus by remember { mutableStateOf("") }
    var editingDeviceName by remember { mutableStateOf<String?>(null) }
    var editingPortValue by remember { mutableStateOf("") }
    var scanningDeviceName by remember { mutableStateOf<String?>(null) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scanStatus by remember { mutableStateOf("") }

    fun connectTo(device: Device) {
        selectedDevice = device
        status = ConnectionStatus.CONNECTING
        errorMessage = ""
        scope.launch {
            try {
                val (success, result) = AdbController.connectWithRetry(device.ip, device.port)
                if (success) {
                    try {
                        ScrcpyController.start(device.ip, device.port, device.name)
                    } catch (e: Exception) {
                        status = ConnectionStatus.ERROR
                        errorMessage = "Connected via ADB, but failed to launch scrcpy: ${e.message}"
                        return@launch
                    }
                    status = ConnectionStatus.CONNECTED
                    SessionStore.saveLastDevice(device.name)
                } else {
                    status = ConnectionStatus.ERROR
                    errorMessage = "Could not connect after several attempts. Detail: $result"
                }
            } catch (e: Exception) {
                status = ConnectionStatus.ERROR
                errorMessage = "Unexpected error while connecting: ${e.message}"
            }
        }
    }

    fun disconnect() {
        ScrcpyController.stop()
        status = ConnectionStatus.DISCONNECTED
        errorMessage = ""
    }

    LaunchedEffect(Unit) {
        val lastDeviceName = SessionStore.loadLastDevice()
        devices.find { it.name == lastDeviceName }?.let { connectTo(it) }
    }

    fun autoDiscoverPort(device: Device) {
        scanningDeviceName = device.name
        scanProgress = 0f
        scanStatus = "Scanning for the current wireless debugging port..."
        scope.launch {
            val foundPort = AdbController.discoverWirelessDebugPort(
                ip = device.ip,
                onProgress = { progress -> scanProgress = progress }
            )
            if (foundPort != null) {
                val updated = devices.map {
                    if (it.name == device.name) it.copy(port = foundPort) else it
                }
                devices = updated
                DeviceStore.save(updated)
                scanStatus = "Found port $foundPort — connecting..."
                scanningDeviceName = null
                connectTo(updated.first { it.name == device.name })
            } else {
                scanStatus = "No wireless debugging port found. Is the phone on and Tailscale connected?"
                scanningDeviceName = null
            }
        }
    }

    val statusText = when (status) {
        ConnectionStatus.DISCONNECTED -> "Disconnected"
        ConnectionStatus.CONNECTING -> "Connecting..."
        ConnectionStatus.CONNECTED -> "Connected to ${selectedDevice?.name}"
        ConnectionStatus.ERROR -> "Connection error"
    }
    val statusColor = when (status) {
        ConnectionStatus.DISCONNECTED -> StatusNeutral
        ConnectionStatus.CONNECTING -> StatusWarning
        ConnectionStatus.CONNECTED -> StatusSuccess
        ConnectionStatus.ERROR -> StatusError
    }
    val isConnected = status == ConnectionStatus.CONNECTED

    // Each panel as a lambda, so we can arrange them into a responsive grid below
    val panels: List<@Composable () -> Unit> = listOf(
        {
            Text("Devices", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(14.dp))

            devices.forEach { device ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.subtitle1)
                        if (editingDeviceName == device.name) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${device.ip}:", style = MaterialTheme.typography.caption)
                                Spacer(Modifier.width(4.dp))
                                OutlinedTextField(
                                    value = editingPortValue,
                                    onValueChange = { editingPortValue = it.filter { c -> c.isDigit() } },
                                    modifier = Modifier.width(90.dp),
                                    textStyle = MaterialTheme.typography.caption,
                                    singleLine = true
                                )
                                IconButton(onClick = {
                                    val newPortValue = editingPortValue.toIntOrNull()
                                    if (newPortValue != null) {
                                        val updated = devices.map {
                                            if (it.name == device.name) it.copy(port = newPortValue) else it
                                        }
                                        devices = updated
                                        DeviceStore.save(updated)
                                    }
                                    editingDeviceName = null
                                }) {
                                    Icon(Icons.Filled.Check, contentDescription = "Save port", tint = StatusSuccess, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            Text("${device.ip}:${device.port}", style = MaterialTheme.typography.caption)
                        }
                    }
                    IconButton(onClick = {
                        if (editingDeviceName == device.name) {
                            editingDeviceName = null
                        } else {
                            editingDeviceName = device.name
                            editingPortValue = device.port.toString()
                        }
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit port", tint = StatusNeutral, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { connectTo(device) }) {
                        Icon(Icons.Filled.Link, contentDescription = "Connect", tint = AccentPrimary)
                    }
                    IconButton(
                        enabled = scanningDeviceName == null,
                        onClick = { autoDiscoverPort(device) }
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Auto-discover port",
                            tint = if (scanningDeviceName == device.name) AccentSecondary else StatusNeutral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (scanningDeviceName == device.name) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = scanProgress,
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentSecondary
                    )
                }
                Divider(color = BorderSubtle)
            }

            if (scanStatus.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(scanStatus, style = MaterialTheme.typography.caption, color = StatusNeutral)
            }

            Spacer(Modifier.height(16.dp))
            Text("Add device", style = MaterialTheme.typography.subtitle2)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = newIp, onValueChange = { newIp = it }, label = { Text("Tailscale IP") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newPort,
                onValueChange = { newPort = it.filter { c -> c.isDigit() } },
                label = { Text("Port (5555 fixed, or wireless debugging's current port)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (newName.isNotBlank() && newIp.isNotBlank()) {
                        val port = newPort.toIntOrNull() ?: 5555
                        val updated = devices + Device(newName, newIp, port)
                        devices = updated
                        DeviceStore.save(updated)
                        newName = ""
                        newIp = ""
                        newPort = "5555"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save device")
            }
        },
        {
            Text("Status & Actions", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(4.dp))
            Text(statusText, color = statusColor, style = MaterialTheme.typography.body1)

            if (status == ConnectionStatus.ERROR) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage, style = MaterialTheme.typography.caption, color = StatusError)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { selectedDevice?.let { connectTo(it) } }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Quick actions", style = MaterialTheme.typography.subtitle2)
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = { scope.launch { AdbController.pressKey(AdbController.KeyCode.HOME) } }, enabled = isConnected) {
                    Icon(Icons.Filled.Home, contentDescription = "Home")
                }
                IconButton(onClick = { scope.launch { AdbController.pressKey(AdbController.KeyCode.BACK) } }, enabled = isConnected) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { scope.launch { AdbController.pressKey(AdbController.KeyCode.RECENTS) } }, enabled = isConnected) {
                    Icon(Icons.Filled.History, contentDescription = "Recents")
                }
                IconButton(onClick = { disconnect() }, enabled = isConnected) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Disconnect", tint = if (isConnected) StatusError else StatusNeutral)
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    unlockStatus = "Waking & unlocking..."
                    scope.launch {
                        AdbController.wakeAndUnlock()
                        unlockStatus = "Sent wake + unlock swipe"
                    }
                }
            ) {
                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Wake & Unlock")
            }
            if (unlockStatus.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(unlockStatus, style = MaterialTheme.typography.caption, color = StatusNeutral)
            }
            Text(
                "Requires the companion app installed on the phone. Only bypasses a swipe-to-unlock screen (no PIN/pattern/fingerprint).",
                style = MaterialTheme.typography.caption,
                color = StatusNeutral
            )
        },
        { FileTransferPanel(enabled = isConnected) },
        { AppsPanel(enabled = isConnected) },
        { SystemPanel(enabled = isConnected) },
        { AutomationPanel(enabled = isConnected) }
    )

    Box(Modifier.fillMaxSize().background(BackgroundDark)) {
        Column(Modifier.fillMaxSize()) {

            // Top bar
            Row(
                Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Remote Control — PC to Phone", style = MaterialTheme.typography.h6)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(statusColor, shape = CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(statusText, color = statusColor, style = MaterialTheme.typography.body1)
                }
            }

            // Responsive grid: measures available width to decide column count, then splits
            // the remaining window height evenly between rows so everything fits without
            // page-level scrolling. Each card scrolls internally only if its own content overflows.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val spacing = 20.dp
                val minCardWidth = 300.dp
                val availableWidth = maxWidth - 56.dp // account for outer padding
                val columns = ((availableWidth + spacing) / (minCardWidth + spacing))
                    .toInt()
                    .coerceIn(1, 3)

                val rows = panels.chunked(columns)

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rows.forEach { rowPanels ->
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            rowPanels.forEach { panelContent ->
                                PanelCard(Modifier.weight(1f).fillMaxHeight()) { panelContent() }
                            }
                            repeat(columns - rowPanels.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Remote Control — PC to Phone") {
        AppTheme {
            var isUnlocked by remember { mutableStateOf(false) }
            if (isUnlocked) {
                App()
            } else {
                LoginScreen(onUnlocked = { isUnlocked = true })
            }
        }
    }
}