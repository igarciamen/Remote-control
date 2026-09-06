package ui

import adb.AdbController
import adb.Script
import adb.ScriptAction
import adb.ScriptPlayer
import adb.ScriptStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class ActionType(val label: String) {
    TAP("Tap"), SWIPE("Swipe"), WAIT("Wait"), TYPE_TEXT("Type text"), PRESS_KEY("Home")
}

@Composable
fun AutomationPanel(enabled: Boolean) {
    val scope = rememberCoroutineScope()

    var scripts by remember { mutableStateOf(ScriptStore.load()) }
    var scriptName by remember { mutableStateOf("") }
    var currentActions by remember { mutableStateOf(listOf<ScriptAction>()) }

    var selectedType by remember { mutableStateOf(ActionType.TAP) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var xInput by remember { mutableStateOf("") }
    var yInput by remember { mutableStateOf("") }
    var x2Input by remember { mutableStateOf("") }
    var y2Input by remember { mutableStateOf("") }
    var textInput by remember { mutableStateOf("") }
    var waitInput by remember { mutableStateOf("1000") }
    var statusMessage by remember { mutableStateOf("") }

    fun addAction() {
        val action = when (selectedType) {
            ActionType.TAP -> ScriptAction(type = "tap", x = xInput.toIntOrNull(), y = yInput.toIntOrNull())
            ActionType.SWIPE -> ScriptAction(
                type = "swipe",
                x = xInput.toIntOrNull(), y = yInput.toIntOrNull(),
                x2 = x2Input.toIntOrNull(), y2 = y2Input.toIntOrNull(),
                durationMs = 300
            )
            ActionType.WAIT -> ScriptAction(type = "wait", waitMs = waitInput.toLongOrNull() ?: 1000)
            ActionType.TYPE_TEXT -> ScriptAction(type = "type_text", text = textInput)
            ActionType.PRESS_KEY -> ScriptAction(type = "press_key", keyCode = AdbController.KeyCode.HOME)
        }
        currentActions = currentActions + action
    }

    Column {
        Text("Automation", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(14.dp))

        Text("Action type", style = MaterialTheme.typography.subtitle2)
        Spacer(Modifier.height(4.dp))

        Box {
            OutlinedButton(onClick = { dropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedType.label)
            }
            DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                ActionType.values().forEach { type ->
                    DropdownMenuItem(onClick = { selectedType = type; dropdownExpanded = false }) {
                        Text(type.label)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selectedType) {
            ActionType.TAP -> Row {
                OutlinedTextField(value = xInput, onValueChange = { xInput = it }, label = { Text("X") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(4.dp))
                OutlinedTextField(value = yInput, onValueChange = { yInput = it }, label = { Text("Y") }, modifier = Modifier.weight(1f))
            }
            ActionType.SWIPE -> Column {
                Row {
                    OutlinedTextField(value = xInput, onValueChange = { xInput = it }, label = { Text("X1") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(value = yInput, onValueChange = { yInput = it }, label = { Text("Y1") }, modifier = Modifier.weight(1f))
                }
                Row {
                    OutlinedTextField(value = x2Input, onValueChange = { x2Input = it }, label = { Text("X2") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(value = y2Input, onValueChange = { y2Input = it }, label = { Text("Y2") }, modifier = Modifier.weight(1f))
                }
            }
            ActionType.WAIT -> OutlinedTextField(value = waitInput, onValueChange = { waitInput = it }, label = { Text("Milliseconds") })
            ActionType.TYPE_TEXT -> OutlinedTextField(value = textInput, onValueChange = { textInput = it }, label = { Text("Text") })
            ActionType.PRESS_KEY -> Text("Will add: press Home", style = MaterialTheme.typography.caption)
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { addAction() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add action")
        }

        Spacer(Modifier.height(12.dp))
        Text("Script actions (${currentActions.size})", style = MaterialTheme.typography.subtitle2)
        LazyColumn(Modifier.height(100.dp)) {
            items(currentActions) { action -> Text(action.toString(), style = MaterialTheme.typography.caption) }
        }

        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedTextField(
                value = scriptName,
                onValueChange = { scriptName = it },
                label = { Text("Script name") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (scriptName.isNotBlank() && currentActions.isNotEmpty()) {
                    val updated = scripts + Script(scriptName, currentActions)
                    scripts = updated
                    ScriptStore.save(updated)
                    statusMessage = "Saved: $scriptName"
                    scriptName = ""
                    currentActions = listOf()
                }
            }) {
                Icon(Icons.Filled.Save, contentDescription = "Save script")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Saved scripts", style = MaterialTheme.typography.subtitle2)
        LazyColumn(Modifier.height(150.dp)) {
            items(scripts) { script ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("${script.name} (${script.actions.size})", style = MaterialTheme.typography.caption)
                    Row {
                        IconButton(
                            enabled = enabled,
                            onClick = {
                                statusMessage = "Playing ${script.name}..."
                                scope.launch {
                                    ScriptPlayer.play(script)
                                    statusMessage = "Finished: ${script.name}"
                                }
                            }
                        ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play") }

                        IconButton(onClick = {
                            val updated = scripts.filter { it.name != script.name }
                            scripts = updated
                            ScriptStore.save(updated)
                            statusMessage = "Deleted: ${script.name}"
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = StatusError) }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(statusMessage, style = MaterialTheme.typography.caption)
    }
}