package com.bank.origination.api.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<DecisionEntity, UUID> {
    Optional<DecisionEntity> findFirstByApplicationIdOrderByDecidedAtDesc(UUID applicationId);
}
