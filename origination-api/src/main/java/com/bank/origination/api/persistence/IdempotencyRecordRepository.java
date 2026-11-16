package com.bank.origination.api.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, IdempotencyRecordEntity.Key> {}
