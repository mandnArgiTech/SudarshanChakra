package com.sudarshanchakra.auth.support;

import java.util.List;

/**
 * Canonical module ids for JWT, gateway enforcement, and UI (dashboard / Android).
 */
public final class ModuleConstants {

    public static final List<String> ALL_MODULES = List.of(
            "alerts",
            "cameras",
            "sirens",
            "water",
            "pumps",
            "zones",
            "devices",
            "workers",
            "analytics"
    );

    private ModuleConstants() {
    }
}
