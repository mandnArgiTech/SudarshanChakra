package com.sudarshanchakra.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.domain.model.AuthResponse
import com.sudarshanchakra.domain.model.LoginRequest
import com.sudarshanchakra.domain.model.RegisterRequest
import com.sudarshanchakra.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val EMAIL_KEY = stringPreferencesKey("email")
        private val ROLE_KEY = stringPreferencesKey("role")
    }

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[TOKEN_KEY] != null
    }

    val currentUser: Flow<User?> = dataStore.data.map { prefs ->
        val id = prefs[USER_ID_KEY] ?: return@map null
        User(
            id = id,
            username = prefs[USERNAME_KEY] ?: "",
            email = prefs[EMAIL_KEY] ?: "",
            role = prefs[ROLE_KEY] ?: ""
        )
    }

    suspend fun login(username: String, password: String): Result<AuthResponse> {
        return try {
            val response = apiService.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val auth = response.body()!!
                saveAuth(auth)
                Result.success(auth)
            } else {
                Result.failure(Exception("Login failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(username: String, email: String, password: String): Result<AuthResponse> {
        return try {
            val response = apiService.register(RegisterRequest(username, email, password))
            if (response.isSuccessful && response.body() != null) {
                val auth = response.body()!!
                saveAuth(auth)
                Result.success(auth)
            } else {
                Result.failure(Exception("Registration failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        dataStore.edit { it.clear() }
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
    }
}
