package com.mentorlens.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mentorlens.auth.User;
import com.mentorlens.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QueueTest {

    @Autowired SubmissionRepository submissions;
    @Autowired UserRepository users;
    @Autowired SubmissionQueue queue;

    @Test
    void startupRequeuesRunningRowsAndStubMarksThemDone() throws Exception {
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
