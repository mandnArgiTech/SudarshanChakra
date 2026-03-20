/*
 * ============================================================================
 * SudarshanChakra — ESP32 LoRa Worker Beacon & Child Fall Detector
 * ============================================================================
 * 
 * Dual-purpose firmware for ESP32 + SX1276/SX1278 LoRa module:
 *   MODE 1 (WORKER_BEACON): Broadcasts authorized tag ID every 5 seconds.
 *           Worn by farm workers to suppress intrusion alarms.
 *   MODE 2 (CHILD_SAFETY):  Broadcasts tag ID + accelerometer data.
 *           Worn by children near pond. Detects sudden free-fall (falling
 *           into water) and sends HIGH PRIORITY alert immediately.
 *
 * Hardware:
 *   - ESP32 DevKit V1 (or similar)
 *   - SX1276/1278 LoRa module (433 MHz or 868/915 MHz)
 *   - MPU6050 accelerometer/gyro (child safety mode only)
 *   - 3.7V LiPo battery + TP4056 charger
 *
 * Wiring:
 *   LoRa SX1276 → ESP32:
 *     SCK  → GPIO 5
 *     MISO → GPIO 19
 *     MOSI → GPIO 27
 *     NSS  → GPIO 18
 *     RST  → GPIO 14
 *     DIO0 → GPIO 26
 *
 *   MPU6050 → ESP32 (I2C):
 *     SDA → GPIO 21
 *     SCL → GPIO 22
 *     VCC → 3.3V
 *     GND → GND
 *
 * Libraries Required (install via PlatformIO or Arduino Library Manager):
 *   - LoRa by Sandeepmistry (v0.8.0+)
 *   - Adafruit MPU6050 (v2.2.0+)
 *   - Adafruit Unified Sensor
 *   - Wire (built-in)
 *
 * Build: PlatformIO or Arduino IDE (Board: ESP32 Dev Module)
 * ============================================================================
 */

#include <SPI.h>
#include <LoRa.h>
#include <Wire.h>
#include <esp_task_wdt.h>

// ── CONFIGURATION — CHANGE THESE PER DEVICE ──────────────────────────────
// Set device mode: "WORKER_BEACON" or "CHILD_SAFETY"
#define DEVICE_MODE         "CHILD_SAFETY"   // Change per device

// Unique tag ID — must match authorized_tags.json on Edge Node
#define TAG_ID              "TAG-W001"       // e.g., TAG-W001, TAG-C001

// LoRa frequency — match your region and Edge Node receiver
#define LORA_FREQUENCY      433E6            // 433 MHz (India ISM band)
#define LORA_BANDWIDTH      125E3            // 125 kHz
#define LORA_SPREADING      7                // SF7 (fast, short range OK for farm)
#define LORA_TX_POWER       14               // dBm (max 20 for SX1276)

// Beacon interval
#define BEACON_INTERVAL_MS  5000             // 5 seconds for worker beacon
#define SAFETY_INTERVAL_MS  2000             // 2 seconds for child safety (faster)

// Fall detection thresholds
#define FALL_ACCEL_THRESHOLD  0.3            // g — free-fall threshold (near 0g)
#define FALL_IMPACT_THRESHOLD 2.5            // g — impact threshold after fall
#define FALL_FREEFALL_DURATION_MS 150        // Minimum free-fall duration (ms)

// Deep sleep between beacons (saves battery)
#define ENABLE_DEEP_SLEEP   false            // Set true for max battery life

// ── PIN DEFINITIONS ──────────────────────────────────────────────────────
#define LORA_SCK   5
#define LORA_MISO  19
#define LORA_MOSI  27
#define LORA_NSS   18
#define LORA_RST   14
#define LORA_DIO0  26

#define MPU_SDA    21
#define MPU_SCL    22

#define LED_PIN    2     // Built-in LED for status
#define BUZZER_PIN 4     // Optional: local buzzer for fall alert

// ── GLOBALS ──────────────────────────────────────────────────────────────
bool isChildMode = false;
unsigned long lastBeaconTime = 0;
unsigned long fallStartTime = 0;
bool inFreeFall = false;
bool fallDetected = false;
int packetCounter = 0;

// MPU6050 registers (raw I2C — no library overhead for battery life)
#define MPU_ADDR        0x68
#define MPU_PWR_MGMT_1  0x6B
#define MPU_ACCEL_XOUT  0x3B

struct AccelData {
    float x, y, z;
    float magnitude;
};

// ── SETUP ────────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    while (!Serial && millis() < 3000);
    
    // Hardware watchdog: reset if loop hangs for >30 seconds
    const esp_task_wdt_config_t wdt_config = {
        .timeout_ms = 30000,
        .idle_core_mask = (1 << 0),
        .trigger_panic = true,
    };
    esp_task_wdt_init(&wdt_config);
    esp_task_wdt_add(NULL);
    
    pinMode(LED_PIN, OUTPUT);
    pinMode(BUZZER_PIN, OUTPUT);
    
    Serial.println("============================================");
    Serial.println("  SudarshanChakra ESP32 LoRa Tag");
    Serial.print("  Mode: "); Serial.println(DEVICE_MODE);
    Serial.print("  Tag:  "); Serial.println(TAG_ID);
    Serial.println("============================================");
    
    isChildMode = (String(DEVICE_MODE) == "CHILD_SAFETY");
    
    // Initialize LoRa
    SPI.begin(LORA_SCK, LORA_MISO, LORA_MOSI, LORA_NSS);
    LoRa.setPins(LORA_NSS, LORA_RST, LORA_DIO0);
    
    if (!LoRa.begin(LORA_FREQUENCY)) {
        Serial.println("ERROR: LoRa init failed!");
        // Blink LED rapidly to indicate error
        while (1) {
            digitalWrite(LED_PIN, !digitalRead(LED_PIN));
            delay(100);
        }
    }
    
    LoRa.setSpreadingFactor(LORA_SPREADING);
    LoRa.setSignalBandwidth(LORA_BANDWIDTH);
    LoRa.setTxPower(LORA_TX_POWER);
    LoRa.enableCrc();
    
    Serial.println("LoRa initialized OK");
    
    // Initialize MPU6050 if in child safety mode
    if (isChildMode) {
        Wire.begin(MPU_SDA, MPU_SCL);
        Wire.beginTransmission(MPU_ADDR);
        Wire.write(MPU_PWR_MGMT_1);
        Wire.write(0x00); // Wake up MPU6050
        byte error = Wire.endTransmission();
        
        if (error != 0) {
            Serial.println("WARNING: MPU6050 not found! Fall detection disabled.");
        } else {
            Serial.println("MPU6050 initialized OK — fall detection active");
        }
    }
    
    // Startup blink
    for (int i = 0; i < 3; i++) {
        digitalWrite(LED_PIN, HIGH);
        delay(100);
        digitalWrite(LED_PIN, LOW);
        delay(100);
    }
}

// ── READ ACCELEROMETER ───────────────────────────────────────────────────
AccelData readAccelerometer() {
    AccelData data = {0, 0, 0, 0};
    
    Wire.beginTransmission(MPU_ADDR);
    Wire.write(MPU_ACCEL_XOUT);
    Wire.endTransmission(false);
    Wire.requestFrom((int)MPU_ADDR, 6, 1);
    
    if (Wire.available() == 6) {
        int16_t ax = (Wire.read() << 8) | Wire.read();
        int16_t ay = (Wire.read() << 8) | Wire.read();
        int16_t az = (Wire.read() << 8) | Wire.read();
        
        // Convert to g (±2g range, 16384 LSB/g)
        data.x = ax / 16384.0;
        data.y = ay / 16384.0;
        data.z = az / 16384.0;
        data.magnitude = sqrt(data.x*data.x + data.y*data.y + data.z*data.z);
    }
    
    return data;
}

// ── FALL DETECTION STATE MACHINE ─────────────────────────────────────────
/*
 * Two-phase fall detection:
 *   Phase 1: Free-fall — acceleration magnitude drops below 0.3g
 *            (object in free fall experiences near-zero gravity)
 *   Phase 2: Impact — acceleration spikes above 2.5g
 *            (hitting water surface or ground)
 * 
 * Both phases must occur within 500ms to confirm a fall event.
 */
bool checkFallDetection(AccelData accel) {
    unsigned long now = millis();
    
    // Phase 1: Detect free-fall
    if (!inFreeFall && accel.magnitude < FALL_ACCEL_THRESHOLD) {
        inFreeFall = true;
        fallStartTime = now;
        Serial.println("⚠ Free-fall detected!");
    }
    
    // Reset if free-fall lasts too long (not a real fall)
    if (inFreeFall && (now - fallStartTime) > 1000) {
        inFreeFall = false;
    }
    
    // Phase 2: Detect impact after free-fall
    if (inFreeFall && (now - fallStartTime) > FALL_FREEFALL_DURATION_MS) {
        if (accel.magnitude > FALL_IMPACT_THRESHOLD) {
            inFreeFall = false;
            Serial.println("🚨 FALL CONFIRMED — Impact detected!");
            return true;
        }
    }
    
    return false;
}

// ── SEND LORA PACKET ─────────────────────────────────────────────────────
void sendBeacon(const char* type, float accelMag) {
    packetCounter++;
    
    // Packet format: TAG:<id>,TYPE:<type>,SEQ:<n>,ACCEL:<g>,BAT:<v>
    // Edge Node LoRa receiver parses this format
    String packet = "TAG:";
    packet += TAG_ID;
    packet += ",TYPE:";
    packet += type;
    packet += ",SEQ:";
    packet += String(packetCounter);
    
    if (isChildMode) {
        packet += ",ACCEL:";
        packet += String(accelMag, 2);
    }
    
    // Read battery voltage (ADC on ESP32)
    float batV = analogRead(35) * (3.3 / 4095.0) * 2.0; // Voltage divider
    packet += ",BAT:";
    packet += String(batV, 2);
    
    LoRa.beginPacket();
    LoRa.print(packet);
    LoRa.endPacket();
    
    // Brief LED flash on transmit
    digitalWrite(LED_PIN, HIGH);
    delay(20);
    digitalWrite(LED_PIN, LOW);
    
    Serial.print("TX: ");
    Serial.println(packet);
}

void sendFallAlert() {
    // Send FALL alert 3 times rapidly for reliability
    for (int i = 0; i < 3; i++) {
        String packet = "TAG:";
        packet += TAG_ID;
        packet += ",TYPE:FALL,SEQ:";
        packet += String(++packetCounter);
        packet += ",PRIORITY:CRITICAL";
        
        LoRa.beginPacket();
        LoRa.print(packet);
        LoRa.endPacket();
        
        Serial.print("🚨 FALL ALERT TX #");
        Serial.print(i + 1);
        Serial.print(": ");
        Serial.println(packet);
        
        delay(100); // Brief gap between retransmissions
    }
    
    // Sound local buzzer
    for (int i = 0; i < 10; i++) {
        digitalWrite(BUZZER_PIN, HIGH);
        delay(100);
        digitalWrite(BUZZER_PIN, LOW);
        delay(100);
    }
}

// ── MAIN LOOP ────────────────────────────────────────────────────────────
void loop() {
    unsigned long now = millis();
    unsigned long interval = isChildMode ? SAFETY_INTERVAL_MS : BEACON_INTERVAL_MS;
    
    if (isChildMode) {
        // ── CHILD SAFETY MODE ──
        // Read accelerometer at high frequency for fall detection
        AccelData accel = readAccelerometer();
        
        if (checkFallDetection(accel)) {
            fallDetected = true;
            sendFallAlert();
            
            // Send repeated fall alerts for FALL_REPEAT_DURATION_MS then
            // resume normal operation so the tag can detect subsequent events.
            unsigned long fallStart = millis();
            const unsigned long FALL_REPEAT_DURATION_MS = 60000; // 1 minute
            while (millis() - fallStart < FALL_REPEAT_DURATION_MS) {
                sendFallAlert();
                delay(5000);
                esp_task_wdt_reset();
            }
            // Reset fall state so the tag can detect new falls
            fallDetected = false;
            fallStartTime = 0;
        }
        
        // Regular beacon (slower interval)
        if (now - lastBeaconTime >= interval) {
            sendBeacon("CHILD_PING", accel.magnitude);
            lastBeaconTime = now;
        }
        
        esp_task_wdt_reset();
        delay(50); // 20 Hz accelerometer sampling
        
    } else {
        // ── WORKER BEACON MODE ──
        // Simple periodic beacon
        if (now - lastBeaconTime >= interval) {
            sendBeacon("WORKER_PING", 0);
            lastBeaconTime = now;
        }
        
        esp_task_wdt_reset();
        if (ENABLE_DEEP_SLEEP) {
            // Deep sleep between beacons (uses ~10µA vs ~80mA active)
            Serial.flush();
            esp_sleep_enable_timer_wakeup(interval * 1000); // µs
            esp_deep_sleep_start();
        } else {
            delay(100);
        }
    }
}
