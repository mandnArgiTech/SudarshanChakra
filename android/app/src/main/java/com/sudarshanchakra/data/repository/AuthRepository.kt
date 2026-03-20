package com.sudarshanchakra.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.data.api.executeApi
import com.sudarshanchakra.data.api.AuthTokenCache
import com.sudarshanchakra.data.security.SecureCredentialStore
import com.sudarshanchakra.domain.model.AuthResponse
import com.sudarshanchakra.domain.model.LoginRequest
import com.sudarshanchakra.domain.model.RegisterRequest
import com.sudarshanchakra.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class RememberedLoginForm(
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
)

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val dataStore: DataStore<Preferences>,
    private val secureCredentialStore: SecureCredentialStore,
    private val tokenCache: AuthTokenCache,
) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val EMAIL_KEY = stringPreferencesKey("email")
        private val ROLE_KEY = stringPreferencesKey("role")

        /** Last username/password form when "Remember me" is enabled (separate from session username). */
        private val REMEMBER_ME_KEY = booleanPreferencesKey("login_remember_me")
        private val SAVED_LOGIN_USERNAME_KEY = stringPreferencesKey("saved_login_username")
    }

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[TOKEN_KEY] != null
    }

    /**
     * Call once at process start (after DataStore is available) so the first API request includes JWT.
     */
    suspend fun syncTokenCacheFromDataStore() {
        val prefs = dataStore.data.first()
        tokenCache.setBearerToken(prefs[TOKEN_KEY])
    }

    /** Used after boot / sync to decide whether to restart MQTT without blocking UI. */
    suspend fun hasAuthSession(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[TOKEN_KEY] != null
    }

    val currentUser: Flow<User?> = dataStore.data.map { prefs ->
        val id = prefs[USER_ID_KEY] ?: return@map null
        User(
            id = id,
            username = prefs[USERNAME_KEY] ?: "",
            email = prefs[EMAIL_KEY] ?: "",
            role = prefs[ROLE_KEY] ?: "",
        )
    }

    suspend fun getRememberedLoginForm(): RememberedLoginForm {
        val prefs = dataStore.data.first()
        // Default checked on first install; explicit false after user logs in without remember.
        val remember = prefs[REMEMBER_ME_KEY] ?: true
        val username = prefs[SAVED_LOGIN_USERNAME_KEY] ?: ""
        val password = if (remember) secureCredentialStore.getPassword().orEmpty() else ""
        return RememberedLoginForm(
            username = username,
            password = password,
            rememberMe = remember,
        )
    }

    suspend fun login(username: String, password: String, rememberMe: Boolean): Result<AuthResponse> {
        return executeApi { apiService.login(LoginRequest(username, password)) }.fold(
            onSuccess = { auth ->
                saveAuth(auth)
                applyRememberMeChoice(username, password, rememberMe)
                Result.success(auth)
            },
            onFailure = { Result.failure(it) },
        )
    }

    suspend fun register(username: String, email: String, password: String): Result<AuthResponse> {
        return executeApi { apiService.register(RegisterRequest(username, email, password)) }.fold(
            onSuccess = { auth ->
                saveAuth(auth)
                applyRememberMeChoice(username, password, rememberMe = false)
                Result.success(auth)
            },
            onFailure = { Result.failure(it) },
        )
    }

    /**
     * Clears session tokens only — keeps server URL prefs and optional "Remember me" form data.
     */
    suspend fun logout() {
        dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(EMAIL_KEY)
            prefs.remove(ROLE_KEY)
        }
        tokenCache.setBearerToken(null)
    }

    private suspend fun saveAuth(auth: AuthResponse) {
        dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = auth.token
            prefs[REFRESH_TOKEN_KEY] = auth.refreshToken
            prefs[USER_ID_KEY] = auth.user.id
            prefs[USERNAME_KEY] = auth.user.username
            prefs[EMAIL_KEY] = auth.user.email
            prefs[ROLE_KEY] = auth.user.role
        }
        tokenCache.setBearerToken(auth.token)
    }

    private suspend fun applyRememberMeChoice(username: String, password: String, rememberMe: Boolean) {
        if (rememberMe) {
            secureCredentialStore.savePassword(password)
            dataStore.edit { prefs ->
                prefs[REMEMBER_ME_KEY] = true
                prefs[SAVED_LOGIN_USERNAME_KEY] = username
            }
        } else {
            secureCredentialStore.clearPassword()
            dataStore.edit { prefs ->
                prefs[REMEMBER_ME_KEY] = false
                prefs.remove(SAVED_LOGIN_USERNAME_KEY)
            }
        }
    }
}
