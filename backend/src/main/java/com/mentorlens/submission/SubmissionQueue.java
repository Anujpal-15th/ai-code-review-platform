package com.mentorlens.submission;

import java.util.EnumSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * The database is the queue: rows with status QUEUED, oldest first. This class only feeds
 * their ids to 4 workers through an unbounded in-memory list, and rebuilds that list on startup.
 */
@Component
public class SubmissionQueue {

    private static final Logger log = LoggerFactory.getLogger(SubmissionQueue.class);

    private final SubmissionRepository submissions;
    private final SubmissionPipeline pipeline;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    SubmissionQueue(SubmissionRepository submissions, SubmissionPipeline pipeline) {
        this.submissions = submissions;
        this.pipeline = pipeline;
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("submission-");
        executor.initialize();
    }

    public void enqueue(UUID id) {
        executor.execute(() -> run(id));
    }

    @EventListener(ApplicationReadyEvent.class)
    void requeueUnfinished() {
        submissions.resetRunning();
        for (Submission s : submissions.findByStatusInOrderByCreatedAtAsc(EnumSet.of(SubmissionStatus.QUEUED))) {
            enqueue(s.getId()); // a duplicate enqueue is harmless: only one worker wins claim()
        }
    }

    private void run(UUID id) {
        try {
            if (submissions.claim(id) != 1) {
                return;
            }
            pipeline.process(id);
        } catch (Exception e) {
            log.error("Submission {} failed", id, e);
            fail(id, e instanceof PipelineException ? e.getMessage() : "Processing failed unexpectedly");
        }
    }

    private void fail(UUID id, String message) {
        try {
            submissions.findById(id).ifPresent(s -> {
                s.setStatus(SubmissionStatus.FAILED);
                s.setError(message);
                submissions.save(s);
            });
        } catch (Exception e) {
            log.error("Could not mark submission {} as FAILED", id, e);
        }
    }
}
