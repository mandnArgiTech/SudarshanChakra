package com.sudarshanchakra.domain.model

data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val user: User
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)
