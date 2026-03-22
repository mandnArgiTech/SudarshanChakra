package com.sudarshanchakra.data.config

import com.sudarshanchakra.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Normalizes REST base URL to include `/api/v1/` and a trailing slash (Retrofit).
 */
object ConnectionUrlNormalizer {

    private const val FALLBACK_API_BASE = "http://127.0.0.1/api/v1/"

    fun defaultApiBaseUrl(): String {
        val s = BuildConfig.API_BASE_URL.trim().trimEnd('/')
        if (s.isEmpty()) return FALLBACK_API_BASE
        val withApi = if (s.contains("/api/", ignoreCase = true)) s else "$s/api/v1"
        val parsed = withApi.toHttpUrlOrNull()
        return (parsed?.toString()?.trimEnd('/') ?: FALLBACK_API_BASE.trimEnd('/')) + "/"
    }

    fun defaultMqttBrokerUrl(): String = BuildConfig.MQTT_BROKER_URL.trim()

    /**
     * @param input e.g. `http://192.168.1.5:8080` or full `https://host/api/v1`
     * @return e.g. `http://192.168.1.5:8080/api/v1/`
     */
    fun normalizeApiBaseUrl(input: String): String {
        val s = input.trim().trimEnd('/')
        if (s.isEmpty()) {
            return defaultApiBaseUrl()
        }
        val withApi = if (s.contains("/api/", ignoreCase = true)) {
            s
        } else {
            "$s/api/v1"
        }
        val parsed = withApi.toHttpUrlOrNull() ?: return defaultApiBaseUrl()
        return parsed.toString().trimEnd('/') + "/"
    }

    /** Validates user input; returns normalized URL or null if unusable. */
    fun validateApiBaseUrl(input: String): String? {
        val s = input.trim().trimEnd('/')
        if (s.isEmpty()) return null
        val withApi = if (s.contains("/api/", ignoreCase = true)) s else "$s/api/v1"
        val parsed = withApi.toHttpUrlOrNull() ?: return null
        return parsed.toString().trimEnd('/') + "/"
    }

    fun normalizeMqttBrokerUrl(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return defaultMqttBrokerUrl()
        return s
    }

    fun validateMqttBrokerUrl(input: String): Boolean {
        val s = input.trim()
        if (s.isEmpty()) return true
        val scheme = s.substringBefore("://", "").lowercase()
        return scheme == "tcp" || scheme == "ssl" || scheme == "tls" ||
            scheme == "mqtt" || scheme == "mqtts"
    }

    /**
     * Edge Flask GUI origin for JPEG snapshots, e.g. `http://192.168.1.10:5000`.
     * Empty string = disabled. Returns null if non-empty but invalid.
     */
    fun validateEdgeGuiBaseUrl(input: String): String? {
        val s = input.trim().trimEnd('/')
        if (s.isEmpty()) return ""
        val parsed = s.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        return parsed.toString().trimEnd('/')
    }
}
