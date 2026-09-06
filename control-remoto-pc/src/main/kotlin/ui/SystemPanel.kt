package ui

import adb.AdbController
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun SystemPanel(enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var wifiOn by remember { mutableStateOf(true) }
    var airplaneOn by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(128f) }
    var showConsole by remember { mutableStateOf(false) }

    Column {
        Text("System", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("WiFi", modifier = Modifier.weight(1f))
            Switch(checked = wifiOn, enabled = enabled, onCheckedChange = {
                wifiOn = it
                scope.launch { AdbController.setWifiEnabled(it) }
            })
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp), tint = StatusNeutral)
            Spacer(Modifier.width(8.dp))
            Text("Bluetooth", modifier = Modifier.weight(1f))
            Switch(checked = false, enabled = false, onCheckedChange = {})
        }
        Text("Not available on this phone (MIUI restriction)", style = MaterialTheme.typography.caption, color = StatusNeutral)

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AirplanemodeActive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Airplane mode", modifier = Modifier.weight(1f))
            Switch(checked = airplaneOn, enabled = enabled, onCheckedChange = {
                airplaneOn = it
                scope.launch { AdbController.setAirplaneMode(it) }
            })
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BrightnessMedium, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Brightness: ${brightness.toInt()}")
        }
        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            onValueChangeFinished = { scope.launch { AdbController.setBrightness(brightness.toInt()) } },
            valueRange = 0f..255f,
            enabled = enabled
        )

        Spacer(Modifier.height(16.dp))
        Button(enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = { showConsole = true }) {
            Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open console")
        }
    }

    if (showConsole) {
        ConsoleDialog(onClose = { showConsole = false })
    }
}

@Composable
private fun ConsoleDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var commandInput by remember { mutableStateOf("") }
    var commandHistory by remember { mutableStateOf(listOf<String>()) }

    fun runCurrentCommand() {
        val cmd = commandInput
        if (cmd.isNotBlank()) {
            scope.launch {
                val output = AdbController.runShell(cmd)
                commandHistory = commandHistory + ("$ $cmd" + if (output.isNotBlank()) "\n$output" else "")
            }
            commandInput = ""
        }
    }

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .size(800.dp, 500.dp)
                .border(4.dp, Color(0xFFFFD54F), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .padding(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Terminal", style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 16.sp))
                Row {
                    TextButton(onClick = { copyToClipboard(commandHistory.joinToString("\n\n")) }) {
                        Text("Copy all", color = Color(0xFF00FF66))
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onClose) { Text("Close", color = Color.White) }
                }
            }

            Spacer(Modifier.height(8.dp))

            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(commandHistory) { entry ->
                        Text(entry, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = Color(0xFF00FF66)))
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$ ", style = TextStyle(color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 16.sp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { runCurrentCommand() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { runCurrentCommand() }) { Text("Run") }
            }
        }
    }
}