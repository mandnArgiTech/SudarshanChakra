package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.dto.FarmRequest;
import com.sudarshanchakra.auth.dto.FarmResponse;
import com.sudarshanchakra.auth.model.Farm;
import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.FarmRepository;
import com.sudarshanchakra.auth.repository.UserRepository;
import com.sudarshanchakra.auth.support.ModuleConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farmRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<FarmResponse> listAll() {
        return farmRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public FarmResponse get(UUID id) {
        Farm farm = farmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Farm not found: " + id));
        return toResponse(farm);
    }

    @Transactional
    public FarmResponse create(FarmRequest request) {
        if (farmRepository.existsBySlug(request.getSlug())) {
            throw new IllegalArgumentException("Slug already exists: " + request.getSlug());
        }
        Farm farm = Farm.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .slug(request.getSlug())
                .ownerName(request.getOwnerName())
                .contactPhone(request.getContactPhone())
                .contactEmail(request.getContactEmail())
                .status(request.getStatus() != null ? request.getStatus() : "active")
                .subscriptionPlan(request.getSubscriptionPlan() != null ? request.getSubscriptionPlan() : "full")
                .modulesEnabled(request.getModulesEnabled() != null && !request.getModulesEnabled().isEmpty()
                        ? request.getModulesEnabled()
                        : ModuleConstants.ALL_MODULES)
                .maxCameras(request.getMaxCameras() != null ? request.getMaxCameras() : 8)
                .maxNodes(request.getMaxNodes() != null ? request.getMaxNodes() : 2)
                .maxUsers(request.getMaxUsers() != null ? request.getMaxUsers() : 10)
                .build();
        farm = farmRepository.save(farm);

        if (request.getInitialAdminUsername() != null && !request.getInitialAdminUsername().isBlank()
                && request.getInitialAdminPassword() != null && !request.getInitialAdminPassword().isBlank()) {
            if (userRepository.existsByUsername(request.getInitialAdminUsername())) {
                throw new IllegalArgumentException("Initial admin username already exists");
            }
            User admin = User.builder()
                    .farmId(farm.getId())
                    .username(request.getInitialAdminUsername().trim())
                    .email(request.getInitialAdminEmail())
                    .passwordHash(passwordEncoder.encode(request.getInitialAdminPassword()))
                    .role(Role.ADMIN)
                    .displayName(request.getInitialAdminDisplayName())
                    .active(true)
                    .build();
            userRepository.save(admin);
            log.info("Created farm {} with initial admin {}", farm.getSlug(), admin.getUsername());
        }

        return toResponse(farm);
    }

    @Transactional
    public FarmResponse update(UUID id, FarmRequest request) {
        Farm farm = farmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Farm not found: " + id));
        if (request.getName() != null) {
            farm.setName(request.getName());
        }
        if (request.getSlug() != null && !request.getSlug().equals(farm.getSlug())) {
            if (farmRepository.existsBySlug(request.getSlug())) {
                throw new IllegalArgumentException("Slug already exists: " + request.getSlug());
            }
            farm.setSlug(request.getSlug());
        }
        if (request.getOwnerName() != null) {
            farm.setOwnerName(request.getOwnerName());
        }
        if (request.getContactPhone() != null) {
            farm.setContactPhone(request.getContactPhone());
        }
        if (request.getContactEmail() != null) {
            farm.setContactEmail(request.getContactEmail());
        }
        if (request.getStatus() != null) {
            farm.setStatus(request.getStatus());
        }
        if (request.getSubscriptionPlan() != null) {
            farm.setSubscriptionPlan(request.getSubscriptionPlan());
        }
        if (request.getModulesEnabled() != null) {
            farm.setModulesEnabled(request.getModulesEnabled());
        }
        if (request.getMaxCameras() != null) {
            farm.setMaxCameras(request.getMaxCameras());
        }
        if (request.getMaxNodes() != null) {
            farm.setMaxNodes(request.getMaxNodes());
        }
        if (request.getMaxUsers() != null) {
            farm.setMaxUsers(request.getMaxUsers());
        }
        return toResponse(farmRepository.save(farm));
    }

    @Transactional
    public FarmResponse suspend(UUID id) {
        Farm farm = farmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Farm not found: " + id));
        farm.setStatus("suspended");
        return toResponse(farmRepository.save(farm));
    }

    @Transactional
    public FarmResponse activate(UUID id) {
        Farm farm = farmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Farm not found: " + id));
        farm.setStatus("active");
        return toResponse(farmRepository.save(farm));
    }

    private FarmResponse toResponse(Farm f) {
        return FarmResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .slug(f.getSlug())
                .ownerName(f.getOwnerName())
                .contactPhone(f.getContactPhone())
                .contactEmail(f.getContactEmail())
                .status(f.getStatus())
                .subscriptionPlan(f.getSubscriptionPlan())
                .modulesEnabled(f.getModulesEnabled())
                .maxCameras(f.getMaxCameras())
                .maxNodes(f.getMaxNodes())
                .maxUsers(f.getMaxUsers())
                .trialEndsAt(f.getTrialEndsAt())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
