package com.bank.origination.api.persistence;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensitiveValidationRepository extends JpaRepository<SensitiveValidationEntity, UUID> {
    List<SensitiveValidationEntity> findAllByOrderByCreatedAtAsc();
}
