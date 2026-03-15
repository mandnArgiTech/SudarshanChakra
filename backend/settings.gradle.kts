rootProject.name = "sudarshanchakra-backend"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(
    "alert-service",
    "device-service",
    "auth-service",
    "siren-service",
    "api-gateway"
)
