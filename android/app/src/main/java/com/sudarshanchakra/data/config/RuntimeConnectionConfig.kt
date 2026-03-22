package com.sudarshanchakra.data.config

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory API base URL and MQTT broker used by OkHttp and [MqttForegroundService].
 * Persisted values are loaded at startup via [com.sudarshanchakra.data.repository.ServerSettingsRepository.hydrateRuntimeFromStore].
 */
@Singleton
class RuntimeConnectionConfig @Inject constructor() {

    private val apiBaseUrl = AtomicReference(ConnectionUrlNormalizer.defaultApiBaseUrl())
    private val mqttBrokerUrl = AtomicReference(ConnectionUrlNormalizer.defaultMqttBrokerUrl())
    /** Edge Flask base URL without trailing slash, or empty if snapshots disabled. */
    private val edgeGuiBaseUrl = AtomicReference("")

    fun getApiBaseUrl(): String = apiBaseUrl.get()

    fun setApiBaseUrl(normalizedUrlWithTrailingSlash: String) {
        apiBaseUrl.set(normalizedUrlWithTrailingSlash.trimEnd('/') + "/")
    }

    fun getMqttBrokerUrl(): String = mqttBrokerUrl.get()

    fun setMqttBrokerUrl(url: String) {
        mqttBrokerUrl.set(url.trim())
    }

    fun getEdgeGuiBaseUrl(): String = edgeGuiBaseUrl.get()

    fun setEdgeGuiBaseUrl(normalizedNoTrailingSlash: String) {
        edgeGuiBaseUrl.set(normalizedNoTrailingSlash.trim().trimEnd('/'))
    }
}
