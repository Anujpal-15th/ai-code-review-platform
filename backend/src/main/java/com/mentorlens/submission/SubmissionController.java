package com.mentorlens.submission;

import com.mentorlens.NoNul;
import com.mentorlens.auth.User;
import com.mentorlens.auth.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/submissions")
class SubmissionController {

    static final int MAX_ACTIVE = 3;

    record FailingTest(@NotBlank @NoNul @Size(max = 5000) String inputText,
                       @NotBlank @NoNul @Size(max = 5000) String expectedText) {}

    record SubmitRequest(
        @NotBlank @Pattern(regexp = "python", message = "must be python") String language,
        @NotBlank @NoNul @Size(max = 5000) String question,
        @NotBlank @NoNul @Size(max = 20000) String code,
        @NotNull @Valid FailingTest failingTest) {}

    private final SubmissionRepository submissions;
    private final UserRepository users;
    private final SubmissionQueue queue;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    SubmissionController(SubmissionRepository submissions, UserRepository users, SubmissionQueue queue,
                         ObjectMapper json, TransactionTemplate tx) {
        this.tx = tx;
        this.submissions = submissions;
        this.users = users;
        this.queue = queue;
        this.json = json;
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubmitRequest r) {
        // committed before enqueue: a worker must be able to see the row
        Submission s = tx.execute(status -> {
            User user = users.findByIdForUpdate(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid token"));
            if (submissions.countByUser_IdAndStatusIn(user.getId(),
                    EnumSet.of(SubmissionStatus.QUEUED, SubmissionStatus.RUNNING)) >= MAX_ACTIVE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You already have " + MAX_ACTIVE + " submissions in progress. Wait for one to finish.");
            }
            return submissions.save(new Submission(user, r.language(), r.question(), r.code(),
                r.failingTest().inputText(), r.failingTest().expectedText()));
        });
        queue.enqueue(s.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("id", s.getId(), "status", "QUEUED"));
    }

    @GetMapping("/{id}")
    Map<String, Object> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Submission s = submissions.findByIdAndUser_Id(id, UUID.fromString(jwt.getSubject()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        long position = s.status == SubmissionStatus.QUEUED
            ? submissions.countByStatusAndCreatedAtBefore(SubmissionStatus.QUEUED, s.createdAt) + 1 : 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", s.status);
        body.put("queuePosition", position);
        body.put("error", s.status == SubmissionStatus.FAILED ? s.error : null);
        body.put("result", s.status == SubmissionStatus.DONE && s.resultJson != null
            ? json.readTree(s.resultJson) : null);
        return body;
    }
}
