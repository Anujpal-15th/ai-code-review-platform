package com.mentorlens.submission;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Serves the stored hints one level at a time. Never calls the AI. */
@RestController
@RequestMapping("/api/submissions")
class HintController {
    private static final int LAST_LEVEL = 4;
    private static final String LAST_TEXT = "Here is the correct version. Compare it with your code.";

    private final SubmissionRepository submissions;
    private final ObjectMapper json;

    HintController(SubmissionRepository submissions, ObjectMapper json) {
        this.submissions = submissions;
        this.json = json;
    }

    // GET changes state on purpose: the API contract makes "next hint" a GET and each call moves the level on.
    @GetMapping("/{id}/hints/next")
    @Transactional
    Map<String, Object> next(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Submission found = submissions.findByIdAndUser_Id(id, UUID.fromString(jwt.getSubject()))
            .filter(s -> s.status == SubmissionStatus.DONE && s.hintLevel != null && s.hintsJson != null
                && s.correctCode != null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hints available"));
        submissions.nextHintLevel(id, LAST_LEVEL); // 0 rows at level 4: stays there
        Submission s = submissions.findById(id).orElseThrow();
        int level = s.hintLevel;
        List<?> hints = json.readValue(found.hintsJson, List.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", level);
        body.put("text", level == LAST_LEVEL ? LAST_TEXT : hints.get(level - 1));
        body.put("correctCode", level == LAST_LEVEL ? s.correctCode : null);
        return body;
    }
}
