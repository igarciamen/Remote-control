package ui

import adb.AdbController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser

@Composable
fun FileTransferPanel(enabled: Boolean) {
    val scope = rememberCoroutineScope()
    var remotePath by remember { mutableStateOf("/sdcard/") }
    var remoteFiles by remember { mutableStateOf(listOf<String>()) }
    var statusMessage by remember { mutableStateOf("") }

    Column {
        Text("Files", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(14.dp))

        Button(
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val chooser = JFileChooser()
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    val localFile = chooser.selectedFile
                    val remoteDest = "/sdcard/Download/${localFile.name}"
                    statusMessage = "Sending ${localFile.name}..."
                    scope.launch {
                        val output = AdbController.pushFile(localFile.absolutePath, remoteDest)
                        statusMessage = "Sent: $output"
                    }
                }
            }
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Send file to phone")
        }

        Spacer(Modifier.height(10.dp))

        Row {
            OutlinedTextField(
                value = remotePath,
                onValueChange = { remotePath = it },
                label = { Text("Phone folder") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                enabled = enabled,
                onClick = {
                    scope.launch {
                        val raw = AdbController.listDirectory(remotePath)
                        remoteFiles = raw.lines().filter { it.isNotBlank() }
                    }
                }
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "List")
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.height(150.dp)) {
            items(remoteFiles) { fileName ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fileName, style = MaterialTheme.typography.caption)
                    IconButton(
                        enabled = enabled,
                        onClick = {
                            val chooser = JFileChooser()
                            chooser.dialogTitle = "Save to..."
                            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            val result = chooser.showSaveDialog(null)
                            if (result == JFileChooser.APPROVE_OPTION) {
                                val destDir = chooser.selectedFile
                                val remoteFull = "$remotePath/$fileName".replace("//", "/")
                                val localDest = File(destDir, fileName).absolutePath
                                statusMessage = "Downloading $fileName..."
                                scope.launch {
                                    val output = AdbController.pullFile(remoteFull, localDest)
                                    statusMessage = "Downloaded: $output"
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Download", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(statusMessage, style = MaterialTheme.typography.caption)
    }
}