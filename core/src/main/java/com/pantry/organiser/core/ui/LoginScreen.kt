package com.pantry.organiser.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: suspend (String, String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var filledLogin by remember { mutableStateOf<SavedLogin?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val passwordManager = remember(activity) { activity?.let(::SystemPasswordManager) }
    val screenScope = rememberCoroutineScope()
    // This screen closes as soon as login succeeds, so the save prompt must outlive it.
    val loginScope = (activity as? LifecycleOwner)?.lifecycleScope ?: screenScope

    // A password from the password manager stays hidden, so nobody holding the device can read it.
    val isPasswordFromManager = filledLogin != null && filledLogin?.password == password

    LaunchedEffect(passwordManager) {
        val savedLogin = passwordManager?.pickSavedLogin() ?: return@LaunchedEffect
        if (email.isEmpty() && password.isEmpty()) {
            email = savedLogin.email
            password = savedLogin.password
            filledLogin = savedLogin
            isPasswordVisible = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pantry Organiser") }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Welcome",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { newValue ->
                    password = when {
                        !isPasswordFromManager -> newValue
                        newValue.startsWith(password) -> newValue.removePrefix(password)
                        else -> ""
                    }
                    errorMessage = null
                },
                label = { Text("Password") },
                visualTransformation = if (isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    if (!isPasswordFromManager) {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter both email and password"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null

                    val login = SavedLogin(email.trim(), password)
                    val isAlreadySaved = login == filledLogin
                    loginScope.launch {
                        val result = onLogin(login.email, login.password)
                        isLoading = false
                        if (result.isFailure) {
                            errorMessage = result.exceptionOrNull()?.message ?: "Login failed"
                        } else if (!isAlreadySaved) {
                            passwordManager?.offerToSave(login)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Login")
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
