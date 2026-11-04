package com.mentorlens.submission;

import com.mentorlens.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Submission {
    @Id @GeneratedValue UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) User user;
    @Column(nullable = false) String language;
    @Column(nullable = false, columnDefinition = "text") String question;
    @Column(nullable = false, columnDefinition = "text") String code;
    @Column(nullable = false, columnDefinition = "text") String failingInput;
    @Column(nullable = false, columnDefinition = "text") String failingExpected;
    @Enumerated(EnumType.STRING) @Column(nullable = false) SubmissionStatus status = SubmissionStatus.QUEUED;
    @Column(columnDefinition = "text") String resultJson;
    @Column(columnDefinition = "text") String hintsJson;
    @Column(columnDefinition = "text") String correctCode; // never returned to the student by GET
    Integer hintLevel; // hint level last shown; null means no checked AI reply (and old rows)
    Instant aiUsedAt; // set when this submission called the AI; drives the daily limit
    @Column(columnDefinition = "text") String error;
    @Column(nullable = false) Instant createdAt = Instant.now();

    protected Submission() {}

    Submission(User user, String language, String question, String code, String failingInput, String failingExpected) {
        this.user = user;
        this.language = language;
        this.question = question;
        this.code = code;
        this.failingInput = failingInput;
        this.failingExpected = failingExpected;
    }

    public UUID getId() { return id; }
    public SubmissionStatus getStatus() { return status; }
    public String getQuestion() { return question; }
    public String getCode() { return code; }
    public String getFailingInput() { return failingInput; }
    public String getFailingExpected() { return failingExpected; }
    public void setStatus(SubmissionStatus status) { this.status = status; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public void setHintsJson(String hintsJson) { this.hintsJson = hintsJson; }
    public void setCorrectCode(String correctCode) { this.correctCode = correctCode; }
    public void setHintLevel(Integer hintLevel) { this.hintLevel = hintLevel; }
    public void setAiUsedAt(Instant aiUsedAt) { this.aiUsedAt = aiUsedAt; }
    public void setError(String error) { this.error = error; }
}
