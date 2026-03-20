package com.sudarshanchakra.data.api

import com.sudarshanchakra.domain.model.Alert

/**
 * Spring Data [org.springframework.data.domain.Page] JSON shape.
 * Gson ignores unknown keys (pageable, sort, etc.).
 */
data class AlertsPageResponse(
    val content: List<Alert> = emptyList(),
)
