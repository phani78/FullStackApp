package com.bank.origination.api.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardProductRepository extends JpaRepository<CardProductEntity, UUID> {
    Optional<CardProductEntity> findByProductCodeAndActiveTrue(String productCode);
    List<CardProductEntity> findAllByActiveTrueOrderByProductCodeAsc();
}
