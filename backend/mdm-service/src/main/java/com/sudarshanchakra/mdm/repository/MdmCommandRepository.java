package com.sudarshanchakra.mdm.repository;

import com.sudarshanchakra.mdm.model.MdmCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MdmCommandRepository extends JpaRepository<MdmCommand, UUID> {

    List<MdmCommand> findByDeviceIdOrderByIssuedAtDesc(UUID deviceId);
}
