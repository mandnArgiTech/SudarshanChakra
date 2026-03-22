package com.sudarshanchakra.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Optional biometric / device-credential gate when returning from background. */
@Singleton
class AppLockPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val LOCK_ON_RESUME_KEY = booleanPreferencesKey("app_lock_require_on_resume")
    }

    val lockOnResumeEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LOCK_ON_RESUME_KEY] == true
    }

    suspend fun setLockOnResume(enabled: Boolean) {
        dataStore.edit { prefs ->
            if (enabled) {
                prefs[LOCK_ON_RESUME_KEY] = true
            } else {
                prefs.remove(LOCK_ON_RESUME_KEY)
            }
        }
    }
}
