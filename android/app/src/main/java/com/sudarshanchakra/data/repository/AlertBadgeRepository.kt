package com.sudarshanchakra.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts unread farm-alert notifications for launcher badge ([NotificationCompat.setNumber]).
 * Cleared when the user opens the alert feed.
 */
@Singleton
open class AlertBadgeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val UNREAD_KEY = intPreferencesKey("alert_launcher_badge_unread")
    }

    val unreadCount: Flow<Int> = dataStore.data.map { prefs -> prefs[UNREAD_KEY] ?: 0 }

    suspend fun increment(): Int {
        var newVal = 0
        dataStore.edit { prefs ->
            newVal = (prefs[UNREAD_KEY] ?: 0) + 1
            prefs[UNREAD_KEY] = newVal
        }
        return newVal
    }

    suspend fun clear() {
        dataStore.edit { it.remove(UNREAD_KEY) }
    }

    suspend fun currentCount(): Int = unreadCount.first()
}
