package com.mentorlens.submission;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    Optional<Submission> findByIdAndUser_Id(UUID id, UUID userId);

    long countByStatusAndCreatedAtBefore(SubmissionStatus status, Instant createdAt);

    @Modifying @Transactional
    @Query("update Submission s set s.status = com.mentorlens.submission.SubmissionStatus.RUNNING"
        + " where s.id = :id and s.status = com.mentorlens.submission.SubmissionStatus.QUEUED")
    int claim(UUID id);

    @Modifying @Transactional
    @Query("update Submission s set s.status = com.mentorlens.submission.SubmissionStatus.QUEUED"
        + " where s.status = com.mentorlens.submission.SubmissionStatus.RUNNING")
    int resetRunning();

    List<Submission> findByStatusInOrderByCreatedAtAsc(Collection<SubmissionStatus> statuses);
}
