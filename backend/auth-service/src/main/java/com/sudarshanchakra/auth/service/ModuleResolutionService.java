package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.model.Farm;
import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.FarmRepository;
import com.sudarshanchakra.auth.support.ModuleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModuleResolutionService {

    private final FarmRepository farmRepository;

    public List<String> resolveModules(User user) {
        if (user.getRole() == Role.SUPER_ADMIN) {
            return ModuleConstants.ALL_MODULES;
        }
        if (user.getModulesOverride() != null && !user.getModulesOverride().isEmpty()) {
            return List.copyOf(user.getModulesOverride());
        }
        return farmRepository.findById(user.getFarmId())
                .map(Farm::getModulesEnabled)
                .filter(m -> m != null && !m.isEmpty())
                .map(List::copyOf)
                .orElse(ModuleConstants.ALL_MODULES);
    }
}
