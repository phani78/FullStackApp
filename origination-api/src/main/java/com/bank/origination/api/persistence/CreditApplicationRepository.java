package com.bank.origination.api.persistence;

import com.bank.origination.domain.ApplicationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditApplicationRepository extends JpaRepository<CreditApplicationEntity, UUID> {
    Optional<CreditApplicationEntity> findByIdAndApplicantEmail(UUID id, String applicantEmail);
    List<CreditApplicationEntity> findTop100ByStatusInOrderBySubmittedAtAsc(List<ApplicationStatus> statuses);
}
