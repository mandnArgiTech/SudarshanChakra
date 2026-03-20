package com.sudarshanchakra.util

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object RelativeTimeFormatter {

    fun format(isoOrRaw: String?): String {
        if (isoOrRaw.isNullOrBlank()) return ""
        val instant = parseInstant(isoOrRaw) ?: return isoOrRaw.take(19).replace("T", " ")
        val now = Instant.now()
        val mins = ChronoUnit.MINUTES.between(instant, now).coerceAtLeast(0)
        return when {
            mins < 1 -> "Just now"
            mins < 60 -> "$mins min ago"
            mins < 24 * 60 -> "${mins / 60} hr ago"
            mins < 48 * 60 -> "Yesterday"
            else -> "${mins / (24 * 60)} days ago"
        }
    }

    private fun parseInstant(s: String): Instant? {
        return try {
            Instant.parse(s)
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
            } catch (_: Exception) {
                val n = s.toLongOrNull() ?: return null
                val ms = if (n < 1_000_000_000_000L) n * 1000 else n
                try {
                    Instant.ofEpochMilli(ms)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
