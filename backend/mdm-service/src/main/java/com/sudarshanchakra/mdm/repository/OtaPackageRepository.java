package com.sudarshanchakra.mdm.repository;

import com.sudarshanchakra.mdm.model.OtaPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtaPackageRepository extends JpaRepository<OtaPackage, UUID> {

    List<OtaPackage> findByFarmIdOrderByCreatedAtDesc(UUID farmId);

    Optional<OtaPackage> findFirstByFarmIdOrderByCreatedAtDesc(UUID farmId);
}
