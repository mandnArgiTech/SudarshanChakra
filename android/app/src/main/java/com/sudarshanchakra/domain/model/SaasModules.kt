package com.sudarshanchakra.domain.model

/** Feature module ids aligned with auth-service JWT and API gateway. */
object SaasModules {
    val ALL: Set<String> = setOf(
        "alerts",
        "cameras",
        "sirens",
        "water",
        "pumps",
        "zones",
        "devices",
        "workers",
        "analytics",
    )
}
