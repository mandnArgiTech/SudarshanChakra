package com.sudarshanchakra.mdm.service;

import com.sudarshanchakra.mdm.config.TenantContext;
import com.sudarshanchakra.mdm.dto.OtaPackageRequest;
import com.sudarshanchakra.mdm.model.OtaPackage;
import com.sudarshanchakra.mdm.repository.OtaPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtaService {

    private final OtaPackageRepository otaPackageRepository;

    private UUID requireFarmId() {
        UUID farmId = TenantContext.getFarmId();
        if (farmId == null) {
            throw new IllegalArgumentException("Farm context required");
        }
        return farmId;
    }

    @Transactional
    public OtaPackage create(OtaPackageRequest request) {
        UUID farmId = requireFarmId();
        String version = request.version().trim();
        if (version.isEmpty()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        OtaPackage pkg = OtaPackage.builder()
                .farmId(farmId)
                .version(version)
                .apkUrl(request.apkUrl().trim())
                .apkSha256(request.apkSha256().trim())
                .apkSizeBytes(request.apkSizeBytes())
                .releaseNotes(request.releaseNotes())
                .mandatory(request.mandatory())
                .build();
        return otaPackageRepository.save(pkg);
    }

    public List<OtaPackage> listByFarm() {
        return otaPackageRepository.findByFarmIdOrderByCreatedAtDesc(requireFarmId());
    }

    public OtaPackage getLatest() {
        UUID farmId = requireFarmId();
        return otaPackageRepository.findFirstByFarmIdOrderByCreatedAtDesc(farmId)
                .orElseThrow(() -> new IllegalArgumentException("No OTA package found for farm"));
    }

    @Transactional
    public void delete(UUID id) {
        UUID farmId = requireFarmId();
        OtaPackage pkg = otaPackageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OTA package not found: " + id));
        if (!pkg.getFarmId().equals(farmId)) {
            throw new SecurityException("OTA package does not belong to caller's farm");
        }
        otaPackageRepository.delete(pkg);
    }
}
