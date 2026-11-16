package com.bank.origination.api.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, UUID> {}
