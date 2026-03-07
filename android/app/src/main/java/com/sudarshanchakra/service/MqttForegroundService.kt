package com.sudarshanchakra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.sudarshanchakra.MainActivity
import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MqttForegroundService : Service() {

    @Inject
    lateinit var apiService: ApiService

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mqttClient: Mqtt5AsyncClient? = null
    private var mqttClientId: String = ""
    private var isConnecting = false
    private var hasSubscribed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification("Connecting to alert broker"))

        if (!isConnecting && mqttClient == null) {
            connectAndSubscribe()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mqttClient?.disconnectWith()?.send()
        mqttClient = null
        super.onDestroy()
    }

    private fun connectAndSubscribe() {
        isConnecting = true
        hasSubscribed = false
        mqttClientId = Constants.MQTT_CLIENT_ID_PREFIX + UUID.randomUUID()
        val client = buildClient(mqttClientId)
        mqttClient = client

        serviceScope.launch {
            updateMqttClientIdOnBackend(mqttClientId)
        }

        client.connectWith().keepAlive(60).send().whenComplete { _, throwable ->
            isConnecting = false
            if (throwable != null) {
                Log.e(TAG, "MQTT connection failed", throwable)
                updateServiceStatus("Unable to connect. Retrying...")
                scheduleReconnect(client)
                return@whenComplete
            }

            updateServiceStatus("Connected. Listening for alerts")
            subscribeToTopic(client, Constants.MQTT_ALERT_TOPIC)
            subscribeToTopic(client, LEGACY_ALERT_TOPIC)
        }
    }

    private fun scheduleReconnect(client: Mqtt5AsyncClient) {
        serviceScope.launch {
            delay(RECONNECT_DELAY_MS)
            if (mqttClient == client) {
                mqttClient = null
                connectAndSubscribe()
            }
        }
    }

    private fun buildClient(clientId: String): Mqtt5AsyncClient {
        val brokerUri = URI(Constants.MQTT_BROKER_URL)
        val scheme = (brokerUri.scheme ?: "tcp").lowercase()
        val port = if (brokerUri.port > 0) brokerUri.port else defaultPortForScheme(scheme)
        val host = brokerUri.host ?: "localhost"
        val builder = MqttClient.builder()
            .useMqttVersion5()
            .identifier(clientId)
            .serverHost(host)
            .serverPort(port)
            .automaticReconnectWithDefaultConfig()

        if (scheme == "ssl" || scheme == "mqtts" || scheme == "tls") {
            builder.sslWithDefaultConfig()
        }

        return builder.buildAsync()
    }

    private fun defaultPortForScheme(scheme: String): Int {
        return if (scheme == "ssl" || scheme == "mqtts" || scheme == "tls") 8883 else 1883
    }

    private fun subscribeToTopic(client: Mqtt5AsyncClient, topic: String) {
        client.subscribeWith()
            .topicFilter(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish -> onAlertMessage(topic, publish) }
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Failed to subscribe topic: $topic", throwable)
                    hasSubscribed = false
                    updateServiceStatus("Connected. Waiting for subscription retry")
                } else {
                    hasSubscribed = true
                    Log.i(TAG, "Subscribed to topic: $topic")
                }
            }
    }

    private fun onAlertMessage(topic: String, publish: Mqtt5Publish) {
        val payload = decodePayload(publish.payload.orElse(null))
        val alertInfo = parseAlertPayload(payload)
        val title = when (alertInfo.priority.lowercase()) {
            "critical" -> "Critical farm alert"
            "high" -> "High priority farm alert"
            "warning" -> "Warning farm alert"
            else -> "Farm alert"
        }
        val body = if (alertInfo.zoneName.isNotBlank() && alertInfo.detectionClass.isNotBlank()) {
            "${alertInfo.detectionClass} detected at ${alertInfo.zoneName}"
        } else {
            "New alert received on $topic"
        }
        showAlertNotification(title, body, payload.hashCode())
    }

    private fun parseAlertPayload(payload: String): AlertPayload {
        if (payload.isBlank()) return AlertPayload()
        return try {
            val json = JSONObject(payload)
            AlertPayload(
                priority = json.optString("priority", "unknown"),
                zoneName = json.optString("zone_name", json.optString("zoneName", "")),
                detectionClass = json.optString("detection_class", json.optString("detectionClass", "hazard"))
            )
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to parse alert payload", ex)
            AlertPayload()
        }
    }

    private fun decodePayload(buffer: ByteBuffer?): String {
        if (buffer == null) return ""
        val copy = buffer.duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "MQTT Background Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Farm Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(alertChannel)
    }

    private fun buildServiceNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SudarshanChakra alert monitor")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateServiceStatus(message: String) {
        NotificationManagerCompat.from(this).notify(
            SERVICE_NOTIFICATION_ID,
            buildServiceNotification(message)
        )
    }

    private fun showAlertNotification(title: String, body: String, alertNotificationId: Int) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(
            ALERT_NOTIFICATION_BASE_ID + kotlin.math.abs(alertNotificationId % 10000),
            notification
        )
    }

    private suspend fun updateMqttClientIdOnBackend(clientId: String) {
        try {
            val response = apiService.updateMqttClientId(mapOf("mqttClientId" to clientId))
            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to update MQTT client ID: ${response.code()}")
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Unable to update MQTT client ID", ex)
        }
    }

    data class AlertPayload(
        val priority: String = "unknown",
        val zoneName: String = "",
        val detectionClass: String = ""
    )

    companion object {
        private const val TAG = "MqttForegroundService"
        private const val ACTION_START = "com.sudarshanchakra.action.START_MQTT_SERVICE"
        private const val ACTION_STOP = "com.sudarshanchakra.action.STOP_MQTT_SERVICE"
        private const val SERVICE_CHANNEL_ID = "sc_mqtt_service"
        private const val ALERT_CHANNEL_ID = "sc_alerts"
        private const val SERVICE_NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_BASE_ID = 2000
        private const val RECONNECT_DELAY_MS = 5000L
        private const val LEGACY_ALERT_TOPIC = "alerts/#"

        fun start(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
