package com.sudarshanchakra.data.api

import retrofit2.Response

/**
 * Shared Retrofit success/error handling to avoid copy-paste across repositories.
 */
suspend fun <T> executeApi(
    block: suspend () -> Response<T>,
): Result<T> {
    return try {
        val response = block()
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            val snippet = try {
                response.errorBody()?.use { it.string().take(256) }
            } catch (_: Exception) {
                null
            }
            val msg = buildString {
                append("HTTP ")
                append(response.code())
                if (!snippet.isNullOrBlank()) {
                    append(": ")
                    append(snippet)
                } else {
                    append(" ")
                    append(response.message())
                }
            }
            Result.failure(Exception(msg))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
