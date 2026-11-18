package com.pantry.organiser.core.ui

import android.app.Activity
import android.util.Log
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException

internal data class SavedLogin(val email: String, val password: String) {
    override fun toString(): String = "SavedLogin(email=$email, password=***)"
}

/** Talks to the device's password manager, such as Google Password Manager. */
internal class SystemPasswordManager(private val activity: Activity) {
    private val credentialManager = CredentialManager.create(activity)

    /** Shows the account picker when a login is saved for this app. Stays silent otherwise. */
    suspend fun pickSavedLogin(): SavedLogin? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetPasswordOption())
            .setPreferImmediatelyAvailableCredentials(true)
            .build()
        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            (credential as? PasswordCredential)?.let { SavedLogin(it.id, it.password) }
        } catch (e: GetCredentialException) {
            Log.d(TAG, "No saved login selected: ${e.type}")
            null
        }
    }

    /** Asks the user whether to save this login to their password manager. */
    suspend fun offerToSave(login: SavedLogin) {
        try {
            credentialManager.createCredential(activity, CreatePasswordRequest(login.email, login.password))
        } catch (e: CreateCredentialException) {
            Log.d(TAG, "Login was not saved: ${e.type}")
        }
    }

    private companion object {
        const val TAG = "SystemPasswordManager"
    }
}
