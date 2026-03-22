package com.sudarshanchakra.domain.model

data class User(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
    val farmId: String? = null,
    val displayName: String? = null,
    val active: Boolean? = null,
    val modules: List<String>? = null,
    val permissions: List<String>? = null,
)
