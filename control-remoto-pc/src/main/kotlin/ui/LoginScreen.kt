package ui

import security.PasswordStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(onUnlocked: () -> Unit) {
    var passwordInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val isFirstRun = !PasswordStore.isPasswordSet()

    Box(Modifier.fillMaxSize().background(BackgroundDark), contentAlignment = Alignment.Center) {
        Column(Modifier.width(320.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (isFirstRun) "Create a password for this app" else "Enter your password",
                style = MaterialTheme.typography.h6
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (isFirstRun) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmInput,
                    onValueChange = { confirmInput = it },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage, color = StatusError, style = MaterialTheme.typography.caption)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isFirstRun) {
                        when {
                            passwordInput.length < 4 -> errorMessage = "Password must be at least 4 characters"
                            passwordInput != confirmInput -> errorMessage = "Passwords don't match"
                            else -> { PasswordStore.setPassword(passwordInput); onUnlocked() }
                        }
                    } else {
                        if (PasswordStore.verify(passwordInput)) {
                            onUnlocked()
                        } else {
                            errorMessage = "Incorrect password"
                            passwordInput = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isFirstRun) "Create password" else "Enter")
            }
        }
    }
}