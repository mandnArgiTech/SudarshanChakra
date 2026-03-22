package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.dto.UserCreateRequest;
import com.sudarshanchakra.auth.dto.UserResponse;
import com.sudarshanchakra.auth.dto.UserUpdateRequest;
import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.FarmRepository;
import com.sudarshanchakra.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModuleResolutionService moduleResolutionService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public List<UserResponse> listForPrincipal(String username) {
        User me = userRepository.findByUsername(username).orElseThrow();
        if (me.getRole() == Role.SUPER_ADMIN) {
            return userRepository.findAll().stream().map(this::toUserResponse).collect(Collectors.toList());
        }
        if (me.getRole() == Role.ADMIN || me.getRole() == Role.MANAGER) {
            return userRepository.findByFarmId(me.getFarmId()).stream()
                    .map(this::toUserResponse)
                    .collect(Collectors.toList());
        }
        throw new AccessDeniedException("Forbidden");
    }

    public UserResponse getByIdForPrincipal(UUID id, String principalName) {
        User me = userRepository.findByUsername(principalName).orElseThrow();
        User target = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        assertCanAccessUser(me, target);
        return toUserResponse(target);
    }

    @Transactional
    public UserResponse createUser(String actorUsername, UserCreateRequest request, String clientIp) {
        User actor = userRepository.findByUsername(actorUsername).orElseThrow();
        if (actor.getRole() != Role.SUPER_ADMIN && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Forbidden");
        }

        UUID farmId = request.getFarmId() != null && actor.getRole() == Role.SUPER_ADMIN
                ? UUID.fromString(request.getFarmId())
                : actor.getFarmId();

        if (actor.getRole() != Role.SUPER_ADMIN && !actor.getFarmId().equals(farmId)) {
            throw new AccessDeniedException("Forbidden");
        }

        if (!farmRepository.existsById(farmId)) {
            throw new IllegalArgumentException("Unknown farm");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Role newRole = Role.fromValue(request.getRole());
        if (newRole == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Cannot assign super_admin via API");
        }

        User user = User.builder()
                .farmId(farmId)
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(newRole)
                .displayName(request.getDisplayName())
                .permissions(request.getPermissions())
                .modulesOverride(request.getModulesOverride())
                .active(true)
                .build();
        user = userRepository.save(user);

        auditService.log(farmId, actor.getId(), "user.create", "user", user.getId().toString(),
                java.util.Map.of("username", user.getUsername(), "role", newRole.getValue()), clientIp);

        log.info("User '{}' created user '{}'", actorUsername, user.getUsername());
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, String actorUsername, UserUpdateRequest request, String clientIp) {
        User actor = userRepository.findByUsername(actorUsername).orElseThrow();
        if (actor.getRole() != Role.SUPER_ADMIN && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Forbidden");
        }
        User target = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        assertCanManageUser(actor, target);

        if (request.getEmail() != null) {
            if (userRepository.existsByEmail(request.getEmail())
                    && (target.getEmail() == null || !target.getEmail().equalsIgnoreCase(request.getEmail()))) {
                throw new IllegalArgumentException("Email already exists");
            }
            target.setEmail(request.getEmail());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            target.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRole() != null) {
            Role r = Role.fromValue(request.getRole());
            if (r == Role.SUPER_ADMIN) {
                throw new IllegalArgumentException("Cannot assign super_admin via API");
            }
            target.setRole(r);
        }
        if (request.getDisplayName() != null) {
            target.setDisplayName(request.getDisplayName());
        }
        if (request.getPermissions() != null) {
            target.setPermissions(request.getPermissions());
        }
        if (request.getModulesOverride() != null) {
            target.setModulesOverride(request.getModulesOverride());
        }
        if (request.getActive() != null) {
            target.setActive(request.getActive());
        }

        userRepository.save(target);

        auditService.log(target.getFarmId(), actor.getId(), "user.update", "user", target.getId().toString(),
                null, clientIp);

        return toUserResponse(target);
    }

    @Transactional
    public void deactivateUser(UUID id, String actorUsername, String clientIp) {
        User actor = userRepository.findByUsername(actorUsername).orElseThrow();
        if (actor.getRole() != Role.SUPER_ADMIN && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Forbidden");
        }
        User target = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        assertCanManageUser(actor, target);
        if (target.getId().equals(actor.getId())) {
            throw new IllegalArgumentException("Cannot deactivate yourself");
        }
        target.setActive(false);
        userRepository.save(target);
        auditService.log(target.getFarmId(), actor.getId(), "user.deactivate", "user", target.getId().toString(),
                null, clientIp);
    }

    @Transactional
    public void updateMqttClientId(String username, String mqttClientId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.setMqttClientId(mqttClientId);
        userRepository.save(user);
        log.info("Updated MQTT client ID for user '{}'", username);
    }

    private void assertCanAccessUser(User actor, User target) {
        if (actor.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (actor.getFarmId().equals(target.getFarmId())
                && (actor.getRole() == Role.ADMIN || actor.getRole() == Role.MANAGER)) {
            return;
        }
        throw new AccessDeniedException("Forbidden");
    }

    private void assertCanManageUser(User actor, User target) {
        if (actor.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (actor.getRole() == Role.ADMIN && actor.getFarmId().equals(target.getFarmId())) {
            return;
        }
        throw new AccessDeniedException("Forbidden");
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().getValue())
                .farmId(user.getFarmId())
                .displayName(user.getDisplayName())
                .active(user.getActive())
                .modules(moduleResolutionService.resolveModules(user))
                .permissions(permissionService.effectivePermissions(user.getRole(), user.getPermissions()))
                .build();
    }
}
