package com.sudarshanchakra.mdm.repository;

import com.sudarshanchakra.mdm.model.MdmDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MdmDeviceRepository extends JpaRepository<MdmDevice, UUID> {

    List<MdmDevice> findByFarmId(UUID farmId);

    Optional<MdmDevice> findByAndroidId(String androidId);
}
