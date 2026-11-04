package com.mentorlens.submission;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;

import com.mentorlens.auth.User;
import com.mentorlens.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ClaimTest {

    @Autowired SubmissionRepository submissions;
    @Autowired UserRepository users;
    @Autowired SubmissionQueue queue;
    @MockitoBean SubmissionPipeline pipeline;

    @Test
    void enqueuedTwiceRunsPipelineOnce() throws Exception {
        User u = users.save(new User("claim@x.com", "hash"));
        Submission s = submissions.save(new Submission(u, "python", "q", "code", "in", "out"));

        queue.enqueue(s.getId());
        queue.enqueue(s.getId());

        verify(pipeline, after(1000).times(1)).process(s.getId());
        verify(pipeline, after(0).times(1)).process(any());
    }
}
