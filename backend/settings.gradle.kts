rootProject.name = "sudarshanchakra-backend"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

include(
    "jwt-support",
    "alert-service",
    "device-service",
    "auth-service",
    "siren-service",
    "mdm-service",
    "api-gateway"
)
