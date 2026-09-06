package ui

import adb.AdbController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun AppsPanel(enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var installedPackages by remember { mutableStateOf(listOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    val filteredPackages = installedPackages.filter { it.contains(searchQuery, ignoreCase = true) }

    fun refreshPackages() {
        scope.launch { installedPackages = AdbController.listInstalledPackages() }
    }

    Column {
        Text("Apps", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(14.dp))

        Button(
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val chooser = JFileChooser()
                chooser.fileFilter = FileNameExtensionFilter("APK files", "apk")
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    val apkFile = chooser.selectedFile
                    statusMessage = "Installing ${apkFile.name}..."
                    scope.launch {
                        val output = AdbController.installApk(apkFile.absolutePath)
                        statusMessage = "Result: $output"
                        refreshPackages()
                    }
                }
            }
        ) {
            Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Install APK")
        }

        Spacer(Modifier.height(10.dp))

        Row {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search package") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(enabled = enabled, onClick = { refreshPackages() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.height(200.dp)) {
            items(filteredPackages) { packageName ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(packageName, style = MaterialTheme.typography.caption)
                    Row {
                        IconButton(
                            enabled = enabled,
                            onClick = { scope.launch { AdbController.forceStop(packageName); statusMessage = "Stopped: $packageName" } }
                        ) { Icon(Icons.Filled.Stop, contentDescription = "Force stop", modifier = Modifier.size(18.dp)) }

                        IconButton(
                            enabled = enabled,
                            onClick = {
                                scope.launch {
                                    val output = AdbController.uninstallApp(packageName)
                                    statusMessage = "Uninstalled: $output"
                                    refreshPackages()
                                }
                            }
                        ) { Icon(Icons.Filled.Delete, contentDescription = "Uninstall", tint = StatusError, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(statusMessage, style = MaterialTheme.typography.caption)
    }
}