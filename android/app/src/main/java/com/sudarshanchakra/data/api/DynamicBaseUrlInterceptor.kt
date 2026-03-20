package com.sudarshanchakra.data.api

import com.sudarshanchakra.data.config.RuntimeConnectionConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Rewrites requests from the fixed Retrofit placeholder host to the user-configured API base.
 * Retrofit is built with [PLACEHOLDER_RETROFIT_BASE]; this interceptor swaps scheme/host/port only.
 */
class DynamicBaseUrlInterceptor @Inject constructor(
    private val runtime: RuntimeConnectionConfig,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val configured = runtime.getApiBaseUrl().trimEnd('/')
        val target = configured.toHttpUrlOrNull() ?: return chain.proceed(request)
        val newUrl = request.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }

    companion object {
        /** Must match path prefix used by [com.sudarshanchakra.di.AppModule.provideRetrofit]. */
        const val PLACEHOLDER_RETROFIT_BASE = "http://127.0.0.1/api/v1/"
    }
}
