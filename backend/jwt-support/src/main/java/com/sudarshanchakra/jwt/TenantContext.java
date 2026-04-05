package com.sudarshanchakra.jwt;

import java.util.UUID;

/**
 * Request-scoped tenant + platform operator flag, set by {@link ResourceServerJwtAuthFilter}.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> FARM_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPER_ADMIN = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID farmId, boolean superAdmin, UUID userId) {
        FARM_ID.set(farmId);
        SUPER_ADMIN.set(superAdmin);
        USER_ID.set(userId);
    }

    public static UUID getFarmId() {
        return FARM_ID.get();
    }

    public static boolean isSuperAdmin() {
        return Boolean.TRUE.equals(SUPER_ADMIN.get());
    }

    /** User UUID from JWT {@code user_id} claim; may be null for legacy tokens. */
    public static UUID getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        FARM_ID.remove();
        SUPER_ADMIN.remove();
        USER_ID.remove();
    }
}
