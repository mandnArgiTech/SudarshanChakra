package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.dto.FarmRequest;
import com.sudarshanchakra.auth.dto.FarmResponse;
import com.sudarshanchakra.auth.model.Farm;
import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.FarmRepository;
import com.sudarshanchakra.auth.repository.UserRepository;
import com.sudarshanchakra.auth.support.ModuleConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FarmServiceTest {

    @Mock
    FarmRepository farmRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    FarmService farmService;

    UUID farmId;
    Farm existingFarm;

    @BeforeEach
    void setUp() {
        farmId = UUID.randomUUID();
        existingFarm = Farm.builder()
                .id(farmId)
                .name("Test Farm")
                .slug("test-farm")
                .status("active")
                .subscriptionPlan("full")
                .modulesEnabled(ModuleConstants.ALL_MODULES)
                .maxCameras(8)
                .maxNodes(2)
                .maxUsers(10)
                .build();
    }

    @Test
    void createFarm_savesAndReturnsResponse() {
        when(farmRepository.existsBySlug("new-slug")).thenReturn(false);
        when(farmRepository.save(any(Farm.class))).thenAnswer(i -> i.getArgument(0));

        FarmRequest req = new FarmRequest();
        req.setName("New Farm");
        req.setSlug("new-slug");

        FarmResponse res = farmService.create(req);

        assertThat(res.getName()).isEqualTo("New Farm");
        assertThat(res.getSlug()).isEqualTo("new-slug");
        assertThat(res.getStatus()).isEqualTo("active");
        assertThat(res.getSubscriptionPlan()).isEqualTo("full");
        assertThat(res.getModulesEnabled()).isEqualTo(ModuleConstants.ALL_MODULES);
        assertThat(res.getMaxCameras()).isEqualTo(8);
        verify(farmRepository).save(any(Farm.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createFarm_duplicateSlug_throwsException() {
        when(farmRepository.existsBySlug("dup")).thenReturn(true);
        FarmRequest req = new FarmRequest();
        req.setName("X");
        req.setSlug("dup");
        assertThatThrownBy(() -> farmService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Slug already exists");
        verify(farmRepository, never()).save(any());
    }

    @Test
    void getFarmById_found_returnsResponse() {
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(existingFarm));
        FarmResponse res = farmService.get(farmId);
        assertThat(res.getId()).isEqualTo(farmId);
        assertThat(res.getName()).isEqualTo("Test Farm");
        assertThat(res.getSlug()).isEqualTo("test-farm");
    }

    @Test
    void getFarmById_notFound_throwsException() {
        when(farmRepository.findById(farmId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> farmService.get(farmId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Farm not found");
    }

    @Test
    void suspendFarm_setsStatusSuspended() {
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(existingFarm));
        when(farmRepository.save(any(Farm.class))).thenAnswer(i -> i.getArgument(0));

        FarmResponse res = farmService.suspend(farmId);

        assertThat(res.getStatus()).isEqualTo("suspended");
        ArgumentCaptor<Farm> cap = ArgumentCaptor.forClass(Farm.class);
        verify(farmRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("suspended");
    }

    @Test
    void createFarm_withInitialAdmin_savesUser() {
        when(farmRepository.existsBySlug("with-admin")).thenReturn(false);
        when(farmRepository.save(any(Farm.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.existsByUsername("admin1")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded");

        FarmRequest req = new FarmRequest();
        req.setName("F");
        req.setSlug("with-admin");
        req.setInitialAdminUsername("admin1");
        req.setInitialAdminPassword("secret");

        farmService.create(req);

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().getUsername()).isEqualTo("admin1");
        assertThat(userCap.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(userCap.getValue().getPasswordHash()).isEqualTo("encoded");
    }

    @Test
    void activateFarm_setsStatusActive() {
        existingFarm.setStatus("suspended");
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(existingFarm));
        when(farmRepository.save(any(Farm.class))).thenAnswer(i -> i.getArgument(0));

        FarmResponse res = farmService.activate(farmId);

        assertThat(res.getStatus()).isEqualTo("active");
    }
}
