package com.sudarshanchakra.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sudarshanchakra.data.config.ConnectionUrlNormalizer
import com.sudarshanchakra.data.config.RuntimeConnectionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ServerSettings(
    val apiBaseUrl: String,
    val mqttBrokerUrl: String,
    /** Edge Flask GUI origin for `/api/snapshot/{cameraId}`; empty = off */
    val edgeGuiBaseUrl: String = "",
)

@Singleton
class ServerSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val runtime: RuntimeConnectionConfig,
) {
    companion object {
        private val API_BASE_KEY = stringPreferencesKey("server_api_base_url")
        private val MQTT_BROKER_KEY = stringPreferencesKey("server_mqtt_broker_url")
        private val EDGE_GUI_BASE_KEY = stringPreferencesKey("server_edge_gui_base_url")
    }

    val settings: Flow<ServerSettings> = dataStore.data.map { prefs ->
        ServerSettings(
            apiBaseUrl = prefs[API_BASE_KEY] ?: ConnectionUrlNormalizer.defaultApiBaseUrl(),
            mqttBrokerUrl = prefs[MQTT_BROKER_KEY] ?: ConnectionUrlNormalizer.defaultMqttBrokerUrl(),
            edgeGuiBaseUrl = prefs[EDGE_GUI_BASE_KEY] ?: "",
        )
    }

    /** Load saved URLs into [RuntimeConnectionConfig] (startup). */
    suspend fun hydrateRuntimeFromStore() {
        val prefs = dataStore.data.first()
        val api = prefs[API_BASE_KEY]
        val mqtt = prefs[MQTT_BROKER_KEY]
        if (!api.isNullOrBlank()) {
            runtime.setApiBaseUrl(ConnectionUrlNormalizer.normalizeApiBaseUrl(api))
        }
        if (!mqtt.isNullOrBlank()) {
            runtime.setMqttBrokerUrl(ConnectionUrlNormalizer.normalizeMqttBrokerUrl(mqtt))
        }
        val edge = prefs[EDGE_GUI_BASE_KEY]?.trim().orEmpty()
        runtime.setEdgeGuiBaseUrl(edge)
    }

    suspend fun save(apiInput: String, mqttInput: String, edgeGuiInput: String): Result<Unit> {
        val apiNorm = ConnectionUrlNormalizer.validateApiBaseUrl(apiInput)
            ?: return Result.failure(IllegalArgumentException("Invalid API base URL"))
        if (mqttInput.isNotBlank() && !ConnectionUrlNormalizer.validateMqttBrokerUrl(mqttInput)) {
            return Result.failure(
                IllegalArgumentException("Invalid MQTT broker URL (use tcp://host:1883 or ssl://host:8883)"),
            )
        }
        val mqttNorm = if (mqttInput.isBlank()) {
            ConnectionUrlNormalizer.defaultMqttBrokerUrl()
        } else {
            ConnectionUrlNormalizer.normalizeMqttBrokerUrl(mqttInput)
        }
        val edgeNorm = ConnectionUrlNormalizer.validateEdgeGuiBaseUrl(edgeGuiInput)
            ?: return Result.failure(
                IllegalArgumentException("Invalid edge GUI URL (use http://host:5000 or leave blank)"),
            )
        runtime.setApiBaseUrl(apiNorm)
        runtime.setMqttBrokerUrl(mqttNorm)
        runtime.setEdgeGuiBaseUrl(edgeNorm)
        dataStore.edit { p ->
            p[API_BASE_KEY] = apiNorm
            if (mqttInput.isBlank()) {
                p.remove(MQTT_BROKER_KEY)
            } else {
                p[MQTT_BROKER_KEY] = mqttNorm
            }
            if (edgeNorm.isEmpty()) {
                p.remove(EDGE_GUI_BASE_KEY)
            } else {
                p[EDGE_GUI_BASE_KEY] = edgeNorm
            }
        }
        return Result.success(Unit)
    }

    suspend fun resetToBuildDefaults(): Result<Unit> {
        val api = ConnectionUrlNormalizer.defaultApiBaseUrl()
        val mqtt = ConnectionUrlNormalizer.defaultMqttBrokerUrl()
        runtime.setApiBaseUrl(api)
        runtime.setMqttBrokerUrl(mqtt)
        runtime.setEdgeGuiBaseUrl("")
        dataStore.edit { p ->
            p.remove(API_BASE_KEY)
            p.remove(MQTT_BROKER_KEY)
            p.remove(EDGE_GUI_BASE_KEY)
        }
        return Result.success(Unit)
    }
}
