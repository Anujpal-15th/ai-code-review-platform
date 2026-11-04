package com.mentorlens.submission;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    Optional<Submission> findByIdAndUser_Id(UUID id, UUID userId);

    long countByStatusAndCreatedAtBefore(SubmissionStatus status, Instant createdAt);

    List<Submission> findByStatusInOrderByCreatedAtAsc(Collection<SubmissionStatus> statuses);
}
