package com.sudarshanchakra.data.api

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory JWT mirror for [AuthInterceptor] so we never [runBlocking] on OkHttp worker threads.
 * Kept in sync from [com.sudarshanchakra.data.repository.AuthRepository] and warmed at app startup.
 */
@Singleton
class AuthTokenCache @Inject constructor() {
    private val tokenRef = AtomicReference<String?>(null)

    fun getBearerToken(): String? = tokenRef.get()

    fun setBearerToken(token: String?) {
        tokenRef.set(token)
    }
}
