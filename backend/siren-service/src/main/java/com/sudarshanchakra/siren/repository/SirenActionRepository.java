package com.sudarshanchakra.siren.repository;

import com.sudarshanchakra.siren.model.SirenAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SirenActionRepository extends JpaRepository<SirenAction, UUID> {

    Page<SirenAction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
