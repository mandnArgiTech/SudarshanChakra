package com.sudarshanchakra.auth.repository;

import com.sudarshanchakra.auth.model.Farm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FarmRepository extends JpaRepository<Farm, UUID> {

    boolean existsBySlug(String slug);

    Optional<Farm> findBySlug(String slug);
}
