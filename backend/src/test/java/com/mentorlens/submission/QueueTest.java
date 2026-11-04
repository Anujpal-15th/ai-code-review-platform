package com.mentorlens.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.mentorlens.auth.User;
import com.mentorlens.auth.UserRepository;
import com.mentorlens.runner.RunResult;
import com.mentorlens.runner.RunnerClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class QueueTest {

    @Autowired SubmissionRepository submissions;
    @Autowired UserRepository users;
    @Autowired SubmissionQueue queue;
    @MockitoBean RunnerClient runner;

    @Test
    void startupRequeuesRunningRowsAndFinishesThem() throws Exception {
        when(runner.run(any(), any())).thenReturn(new RunResult("UNSUPPORTED", null, "", null, List.of()));
        User u = users.save(new User("queue@x.com", "hash"));
        Submission s = new Submission(u, "python", "q", "code", "in", "out");
        s.setStatus(SubmissionStatus.RUNNING); // left over from a crashed server
        submissions.save(s);

        queue.requeueUnfinished();

        for (int i = 0; i < 50 && submissions.findById(s.getId()).get().getStatus() != SubmissionStatus.DONE; i++) {
            Thread.sleep(100);
        }
        assertEquals(SubmissionStatus.DONE, submissions.findById(s.getId()).get().getStatus());
    }
}
