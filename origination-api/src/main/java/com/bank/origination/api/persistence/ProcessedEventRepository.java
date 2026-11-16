package com.bank.origination.api.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.Key> {}
