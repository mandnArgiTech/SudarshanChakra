package com.sudarshanchakra.data.config

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory API base URL and MQTT broker used by OkHttp and [MqttForegroundService].
 * Persisted values are loaded at startup via [SettingsHydrator]; user updates via settings UI.
 */
@Singleton
class RuntimeConnectionConfig @Inject constructor() {

    private val apiBaseUrl = AtomicReference(ConnectionUrlNormalizer.defaultApiBaseUrl())
    private val mqttBrokerUrl = AtomicReference(ConnectionUrlNormalizer.defaultMqttBrokerUrl())

    fun getApiBaseUrl(): String = apiBaseUrl.get()

    fun setApiBaseUrl(normalizedUrlWithTrailingSlash: String) {
        apiBaseUrl.set(normalizedUrlWithTrailingSlash.trimEnd('/') + "/")
    }

    fun getMqttBrokerUrl(): String = mqttBrokerUrl.get()

    fun setMqttBrokerUrl(url: String) {
        mqttBrokerUrl.set(url.trim())
    }
}
