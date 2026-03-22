package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.model.Role;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Effective permissions: explicit user {@code permissions} JSON overrides role defaults when non-empty.
 */
@Service
public class PermissionService {

    public List<String> effectivePermissions(Role role, List<String> userOverrides) {
        if (userOverrides != null && !userOverrides.isEmpty()) {
            return List.copyOf(userOverrides);
        }
        return List.copyOf(defaultsForRole(role));
    }

    private Set<String> defaultsForRole(Role role) {
        Set<String> p = new LinkedHashSet<>();
        switch (role) {
            case SUPER_ADMIN -> {
                p.addAll(PermissionMatrix.ALL);
            }
            case ADMIN -> {
                p.addAll(PermissionMatrix.ALL);
                p.remove("farms:manage"); // platform-level; farm admin manages own tenant via users:*
            }
            case MANAGER -> {
                p.addAll(PermissionMatrix.MANAGER);
            }
            case OPERATOR -> {
                p.addAll(PermissionMatrix.OPERATOR);
            }
            case VIEWER -> p.addAll(PermissionMatrix.VIEWER);
        }
        return p;
    }

    private static final class PermissionMatrix {
        static final Set<String> ALL = Set.of(
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

        static final Set<String> MANAGER = new LinkedHashSet<>(List.of(
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
        ));

        static final Set<String> OPERATOR = Set.of(
                "alerts:view",
                "alerts:acknowledge",
                "cameras:view",
                "cameras:ptz",
                "zones:view",
                "sirens:view",
                "devices:view",
                "water:view",
                "pumps:view",
                "analytics:view"
        );

        static final Set<String> VIEWER = Set.of(
                "alerts:view",
                "cameras:view",
                "zones:view",
                "sirens:view",
                "devices:view",
                "water:view",
                "pumps:view",
                "analytics:view"
        );

        private PermissionMatrix() {
        }
    }
}
