package com.sudarshanchakra.domain.model

data class SirenAction(
    val id: String = "",
    val nodeId: String = "",
    val action: String = "",
    val triggeredBy: String? = null,
    val timestamp: String = "",
    val reason: String? = null,
)
