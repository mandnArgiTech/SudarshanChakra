/*
 * ============================================================================
 * SudarshanChakra — ESP32 LoRa-to-Serial Bridge (RECEIVER)
 * ============================================================================
 * 
 * This firmware runs on the ESP32 LoRa module plugged into the Edge Node
 * via USB. It receives LoRa packets from worker beacons and child safety
 * tags, appends RSSI, and forwards them over USB-Serial to the Edge Node
 * Python software (lora_receiver.py).
 *
 * This is the RECEIVER. The TRANSMITTER firmware is esp32_lora_tag.ino.
 *
 * Hardware:
 *   - ESP32 DevKit V1
 *   - SX1276/SX1278 LoRa module (same frequency as transmitter tags)
 *   - USB cable to Edge Node PC
 *   - No battery needed — powered by USB
 *
 * Wiring: Same as esp32_lora_tag.ino LoRa pins (see that file for pinout)
 *
 * Output format (over USB-Serial at 115200 baud):
 *   TAG:<id>,TYPE:<type>,SEQ:<n>,ACCEL:<g>,BAT:<v>,RSSI:<dBm>
 *
 * The Edge Node's lora_receiver.py reads lines from /dev/ttyUSB0 and
 * parses this format.
 * ============================================================================
 */

#include <SPI.h>
#include <LoRa.h>
#include <esp_task_wdt.h>

// ── LoRa Configuration (must match transmitter tags) ─────────────────────
#define LORA_FREQUENCY    433E6      // Must match esp32_lora_tag.ino
#define LORA_BANDWIDTH    125E3
#define LORA_SPREADING    7          // SF7
#define LORA_SYNC_WORD    0x12       // Default sync word

// ── Pin Definitions (same as transmitter) ────────────────────────────────
#define LORA_SCK   5
#define LORA_MISO  19
#define LORA_MOSI  27
#define LORA_NSS   18
#define LORA_RST   14
#define LORA_DIO0  26
#define LED_PIN    2

// ── Stats ────────────────────────────────────────────────────────────────
unsigned long packetsReceived = 0;
unsigned long lastStatsTime = 0;

void setup() {
    Serial.begin(115200);
    while (!Serial && millis() < 3000);

    const esp_task_wdt_config_t wdt_config = {
        .timeout_ms = 30000,
        .idle_core_mask = (1 << 0),
        .trigger_panic = true,
    };
    esp_task_wdt_init(&wdt_config);
    esp_task_wdt_add(NULL);

    pinMode(LED_PIN, OUTPUT);

    Serial.println("# ============================================");
    Serial.println("# SudarshanChakra LoRa Bridge Receiver");
    Serial.println("# Forwarding LoRa packets to USB-Serial");
    Serial.println("# ============================================");

    // Initialize LoRa
    SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_NSS);
    LoRa.setPins(LORA_NSS, LORA_RST, LORA_DIO0);

    if (!LoRa.begin(LORA_FREQUENCY)) {
        Serial.println("# ERROR: LoRa init failed!");
        while (1) {
            digitalWrite(LED_PIN, !digitalRead(LED_PIN));
            delay(100);
        }
    }

    LoRa.setSpreadingFactor(LORA_SPREADING);
    LoRa.setSignalBandwidth(LORA_BANDWIDTH);
    LoRa.enableCrc();

    // Continuous receive mode
    LoRa.receive();

    Serial.println("# LoRa receiver initialized. Listening...");
    Serial.println("# Format: TAG:<id>,TYPE:<type>,SEQ:<n>,...,RSSI:<dBm>");

    // Ready blink
    for (int i = 0; i < 5; i++) {
        digitalWrite(LED_PIN, HIGH);
        delay(50);
        digitalWrite(LED_PIN, LOW);
        delay(50);
    }
}

void loop() {
    esp_task_wdt_reset();
    int packetSize = LoRa.parsePacket();

    if (packetSize > 0) {
        // Read packet content
        String packet = "";
        while (LoRa.available()) {
            packet += (char)LoRa.read();
        }

        // Get RSSI of received packet
        int rssi = LoRa.packetRssi();

        // Append RSSI to packet and forward to USB-Serial
        // lora_receiver.py parses this line format
        String output = packet + ",RSSI:" + String(rssi);
        Serial.println(output);

        // LED flash on receive
        digitalWrite(LED_PIN, HIGH);
        delay(30);
        digitalWrite(LED_PIN, LOW);

        packetsReceived++;
    }

    // Print stats every 60 seconds (lines starting with # are ignored by parser)
    if (millis() - lastStatsTime > 60000) {
        Serial.print("# Stats: ");
        Serial.print(packetsReceived);
        Serial.print(" packets received, uptime ");
        Serial.print(millis() / 1000);
        Serial.println("s");
        lastStatsTime = millis();
    }
}
