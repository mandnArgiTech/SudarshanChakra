package com.sudarshanchakra.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the login password only when the user enables "Remember me".
 * Uses AES encrypted SharedPreferences (not plain DataStore).
 *
 * After backup/restore, OS upgrades, or keystore rotation, [EncryptedSharedPreferences] can throw
 * [javax.crypto.AEADBadTagException] when opening the old file. We clear the unreadable store and
 * recreate so the app does not crash; the user may need to sign in again.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        openEncryptedPrefsWithRecovery()
    }

    private fun openEncryptedPrefsWithRecovery(): SharedPreferences {
        return try {
            createEncryptedPrefs()
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Encrypted credential store unreadable; clearing (e.g. restore/key mismatch)", e)
            wipeAndRetry()
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted credential store failed to open; clearing", e)
            wipeAndRetry()
        }
    }

    private fun wipeAndRetry(): SharedPreferences {
        try {
            context.deleteSharedPreferences(PREFS_NAME)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete encrypted prefs name=$PREFS_NAME", e)
        }
        return try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Encrypted prefs still failing after wipe; using non-encrypted fallback (remembered password cleared)",
                e,
            )
            // Last resort: avoid crash; weaker storage only if keystore is broken
            context.getSharedPreferences("${PREFS_NAME}_plain_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun savePassword(password: String) {
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun clearPassword() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val TAG = "SecureCredentialStore"
        private const val PREFS_NAME = "sc_remembered_login"
        private const val KEY_PASSWORD = "password"
    }
}
