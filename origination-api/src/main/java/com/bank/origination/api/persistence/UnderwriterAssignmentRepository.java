package com.bank.origination.api.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnderwriterAssignmentRepository extends JpaRepository<UnderwriterAssignmentEntity, UUID> {
    Optional<UnderwriterAssignmentEntity> findFirstByApplicationIdOrderByAssignedAtDesc(UUID applicationId);
}
