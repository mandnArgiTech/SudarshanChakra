package com.sudarshanchakra.data.api

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches Authorization header from [AuthTokenCache] (no blocking I/O on OkHttp threads).
 */
class AuthInterceptor @Inject constructor(
    private val tokenCache: AuthTokenCache,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenCache.getBearerToken()
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
