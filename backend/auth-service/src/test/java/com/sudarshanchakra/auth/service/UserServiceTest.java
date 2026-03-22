package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.FarmRepository;
import com.sudarshanchakra.auth.repository.UserRepository;
import com.sudarshanchakra.auth.support.ModuleConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private ModuleResolutionService moduleResolutionService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    @Test
    void listForPrincipal_superAdmin_returnsAll() {
        UUID id = UUID.randomUUID();
        User admin = User.builder()
                .id(id)
                .farmId(UUID.randomUUID())
                .username("sa")
                .passwordHash("h")
                .role(Role.SUPER_ADMIN)
                .active(true)
                .build();
        User u = User.builder()
                .id(UUID.randomUUID())
                .farmId(UUID.randomUUID())
                .username("a")
                .email("a@x.com")
                .passwordHash("h")
                .role(Role.VIEWER)
                .active(true)
                .build();
        when(userRepository.findByUsername("sa")).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(u));
        when(moduleResolutionService.resolveModules(u)).thenReturn(ModuleConstants.ALL_MODULES);
        when(permissionService.effectivePermissions(u.getRole(), u.getPermissions())).thenReturn(List.of());

        var list = userService.listForPrincipal("sa");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getUsername()).isEqualTo("a");
    }

    @Test
    void listForPrincipal_viewer_forbidden() {
        User v = User.builder()
                .id(UUID.randomUUID())
                .farmId(UUID.randomUUID())
                .username("v")
                .passwordHash("h")
                .role(Role.VIEWER)
                .active(true)
                .build();
        when(userRepository.findByUsername("v")).thenReturn(Optional.of(v));
        assertThatThrownBy(() -> userService.listForPrincipal("v"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getByIdForPrincipal_adminSameFarm_ok() {
        UUID farm = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        User admin = User.builder()
                .id(UUID.randomUUID())
                .farmId(farm)
                .username("adm")
                .passwordHash("h")
                .role(Role.ADMIN)
                .active(true)
                .build();
        User target = User.builder()
                .id(uid)
                .farmId(farm)
                .username("u")
                .passwordHash("h")
                .role(Role.VIEWER)
                .active(true)
                .build();
        when(userRepository.findByUsername("adm")).thenReturn(Optional.of(admin));
        when(userRepository.findById(uid)).thenReturn(Optional.of(target));
        when(moduleResolutionService.resolveModules(target)).thenReturn(ModuleConstants.ALL_MODULES);
        when(permissionService.effectivePermissions(target.getRole(), target.getPermissions())).thenReturn(List.of());

        assertThat(userService.getByIdForPrincipal(uid, "adm").getUsername()).isEqualTo("u");
    }

    @Test
    void updateMqttClientId_saves() {
        User u = User.builder()
                .id(UUID.randomUUID())
                .farmId(UUID.randomUUID())
                .username("bob")
                .passwordHash("h")
                .role(Role.MANAGER)
                .active(true)
                .build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(u));

        userService.updateMqttClientId("bob", "mqtt-123");

        verify(userRepository).save(u);
        assertThat(u.getMqttClientId()).isEqualTo("mqtt-123");
    }
}
