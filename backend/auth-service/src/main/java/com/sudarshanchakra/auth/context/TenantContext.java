package com.sudarshanchakra.auth.context;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Request-scoped tenant context populated from JWT + DB (see {@code JwtAuthFilter}).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> FARM_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> MODULES = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID farmId, UUID userId, List<String> modules) {
        FARM_ID.set(farmId);
        USER_ID.set(userId);
        MODULES.set(modules == null ? null : List.copyOf(modules));
    }

    public static UUID getFarmId() {
        return FARM_ID.get();
    }

    public static UUID getUserId() {
        return USER_ID.get();
    }

    public static List<String> getModules() {
        List<String> m = MODULES.get();
        return m == null ? Collections.emptyList() : m;
    }

    public static void clear() {
        FARM_ID.remove();
        USER_ID.remove();
        MODULES.remove();
    }
}
