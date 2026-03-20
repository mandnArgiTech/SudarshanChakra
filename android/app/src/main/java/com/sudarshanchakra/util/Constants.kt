package com.sudarshanchakra.util

/**
 * API base URL and MQTT broker are user-configurable (Profile / login → Server connection)
 * and stored in DataStore; build-time defaults live in [com.sudarshanchakra.BuildConfig]
 * via [com.sudarshanchakra.data.config.ConnectionUrlNormalizer].
 */
object Constants {
    const val DATASTORE_NAME = "sudarshanchakra_prefs"
    const val TOKEN_KEY = "auth_token"
    const val REFRESH_TOKEN_KEY = "refresh_token"
    const val USER_ID_KEY = "user_id"

    const val MQTT_CLIENT_ID_PREFIX = "sc-android-"
    const val MQTT_ALERT_TOPIC = "farm/alerts/#"
    const val MQTT_SIREN_TOPIC = "siren/#"

    const val ALERT_CACHE_MAX_AGE_HOURS = 24L

    // Water level MQTT topics
    const val MQTT_WATER_LEVEL_TOPIC  = "+/water/level"   // wildcard — any tank device
    const val MQTT_WATER_STATUS_TOPIC = "+/water/status"
    const val MQTT_MOTOR_STATUS_TOPIC = "+/motor/status"
    const val MQTT_MOTOR_ALERT_TOPIC  = "+/motor/alert"
}
