package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.model.Farm;
import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.FarmRepository;
import com.sudarshanchakra.auth.support.ModuleConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleResolutionServiceTest {

    @Mock
    FarmRepository farmRepository;

    @InjectMocks
    ModuleResolutionService moduleResolutionService;

    @Test
    void resolveModules_farmModules_returnsFarmList() {
        UUID farmId = UUID.randomUUID();
        User user = User.builder()
                .farmId(farmId)
                .role(Role.ADMIN)
                .modulesOverride(null)
                .build();
        Farm farm = Farm.builder()
                .id(farmId)
                .modulesEnabled(List.of("alerts", "cameras"))
                .build();
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));

        List<String> modules = moduleResolutionService.resolveModules(user);

        assertThat(modules).containsExactly("alerts", "cameras");
    }

    @Test
    void resolveModules_userOverride_returnsUserList() {
        UUID farmId = UUID.randomUUID();
        User user = User.builder()
                .farmId(farmId)
                .role(Role.ADMIN)
                .modulesOverride(List.of("alerts", "water"))
                .build();

        List<String> modules = moduleResolutionService.resolveModules(user);

        assertThat(modules).containsExactly("alerts", "water");
        verifyNoInteractions(farmRepository);
    }

    @Test
    void resolveModules_emptyModules_returnsAllModules() {
        UUID farmId = UUID.randomUUID();
        User user = User.builder()
                .farmId(farmId)
                .role(Role.ADMIN)
                .modulesOverride(null)
                .build();
        Farm farm = Farm.builder()
                .id(farmId)
                .modulesEnabled(null)
                .build();
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));

        List<String> modules = moduleResolutionService.resolveModules(user);

        assertThat(modules).isEqualTo(ModuleConstants.ALL_MODULES);
    }

    @Test
    void resolveModules_superAdmin_returnsAllModules() {
        User user = User.builder()
                .farmId(UUID.randomUUID())
                .role(Role.SUPER_ADMIN)
                .modulesOverride(List.of("alerts"))
                .build();

        List<String> modules = moduleResolutionService.resolveModules(user);

        assertThat(modules).isEqualTo(ModuleConstants.ALL_MODULES);
        verifyNoInteractions(farmRepository);
    }
}
