package com.mentorlens.submission;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stub: later tasks replace the body with the runner, comparison and AI steps. */
@Component
public class SubmissionPipeline {

    private final SubmissionRepository submissions;

    SubmissionPipeline(SubmissionRepository submissions) {
        this.submissions = submissions;
    }

    public void process(UUID id) {
        Submission s = submissions.findById(id).orElseThrow();
        s.setResultJson("{\"outcome\":\"UNSUPPORTED\",\"passed\":false}");
        s.setStatus(SubmissionStatus.DONE);
        submissions.save(s);
    }
}
