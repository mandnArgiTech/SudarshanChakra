package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionServiceTest {

    private static final Set<String> SUPER_ADMIN_ALL = Set.of(
            "farms:manage",
            "farms:view",
            "users:manage",
            "users:view",
            "alerts:view",
            "alerts:acknowledge",
            "alerts:resolve",
            "cameras:view",
            "cameras:manage",
            "cameras:ptz",
            "zones:view",
            "zones:manage",
            "sirens:view",
            "sirens:trigger",
            "devices:view",
            "devices:manage",
            "water:view",
            "water:manage",
            "pumps:view",
            "pumps:control",
            "analytics:view",
            "settings:manage",
            "audit:view"
    );

    PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService();
    }

    @Test
    void superAdmin_hasAllPermissions() {
        List<String> perms = permissionService.effectivePermissions(Role.SUPER_ADMIN, null);
        assertThat(perms).containsExactlyInAnyOrderElementsOf(SUPER_ADMIN_ALL);
    }

    @Test
    void viewer_cannotTriggerSiren() {
        List<String> perms = permissionService.effectivePermissions(Role.VIEWER, null);
        assertThat(perms).doesNotContain("sirens:trigger");
    }

    @Test
    void viewer_canViewAlerts() {
        List<String> perms = permissionService.effectivePermissions(Role.VIEWER, null);
        assertThat(perms).contains("alerts:view");
    }

    @Test
    void manager_canAcknowledgeAlerts() {
        List<String> perms = permissionService.effectivePermissions(Role.MANAGER, null);
        assertThat(perms).contains("alerts:acknowledge");
    }

    @Test
    void operator_cannotDeleteZones() {
        List<String> perms = permissionService.effectivePermissions(Role.OPERATOR, null);
        assertThat(perms).doesNotContain("zones:manage");
    }
}
