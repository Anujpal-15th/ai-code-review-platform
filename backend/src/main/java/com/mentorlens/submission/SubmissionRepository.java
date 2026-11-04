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

    long countByUser_IdAndStatusIn(UUID userId, Collection<SubmissionStatus> statuses);

    long countByStatusAndCreatedAtBefore(SubmissionStatus status, Instant createdAt);

    @Modifying @Transactional
    @Query("update Submission s set s.status = com.mentorlens.submission.SubmissionStatus.RUNNING"
        + " where s.id = :id and s.status = com.mentorlens.submission.SubmissionStatus.QUEUED")
    int claim(UUID id);

    @Modifying @Transactional
    @Query("update Submission s set s.status = com.mentorlens.submission.SubmissionStatus.QUEUED"
        + " where s.status = com.mentorlens.submission.SubmissionStatus.RUNNING")
    int resetRunning();

    /** AI uses since the given time by this submission's student, not counting this one (it may be a restart). */
    @Query("select count(o) from Submission o where o.aiUsedAt >= :since and o.id <> :id"
        + " and o.user.id = (select s.user.id from Submission s where s.id = :id)")
    long countAiUsesBySameStudent(UUID id, Instant since);

    /** True when this submission's student is in the explanation-only pilot group; unset counts as step-based. */
    @Query("select count(s) > 0 from Submission s where s.id = :id"
        + " and s.user.pilotGroup = com.mentorlens.auth.PilotGroup.EXPLANATION_ONLY")
    boolean isExplanationOnly(UUID id);

    /** One atomic step up; the row lock also makes concurrent callers take turns. Stops at the last level. */
    @Modifying(clearAutomatically = true) @Transactional
    @Query("update Submission s set s.hintLevel = s.hintLevel + 1 where s.id = :id and s.hintLevel < :max")
    int nextHintLevel(UUID id, int max);

    List<Submission> findByStatusInOrderByCreatedAtAsc(Collection<SubmissionStatus> statuses);
}
