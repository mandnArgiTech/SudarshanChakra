package com.sudarshanchakra.util

import com.sudarshanchakra.BuildConfig

object Constants {
    val API_BASE_URL: String = BuildConfig.API_BASE_URL
    val MQTT_BROKER_URL: String = BuildConfig.MQTT_BROKER_URL

    const val DATASTORE_NAME = "sudarshanchakra_prefs"
    const val TOKEN_KEY = "auth_token"
    const val REFRESH_TOKEN_KEY = "refresh_token"
    const val USER_ID_KEY = "user_id"

    const val MQTT_CLIENT_ID_PREFIX = "sc-android-"
    const val MQTT_ALERT_TOPIC = "farm/alerts/#"
    const val MQTT_SIREN_TOPIC = "siren/#"

    const val ALERT_CACHE_MAX_AGE_HOURS = 24L
}
