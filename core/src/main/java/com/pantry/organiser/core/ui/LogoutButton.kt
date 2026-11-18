package com.pantry.organiser.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun LogoutButton(
    onConfirmLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmation by remember { mutableStateOf(false) }

    IconButton(onClick = { showConfirmation = true }, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Log out?") },
            text = { Text("You will need to sign in again to use the app.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmation = false
                        onConfirmLogout()
                    }
                ) {
                    Text("Log out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
