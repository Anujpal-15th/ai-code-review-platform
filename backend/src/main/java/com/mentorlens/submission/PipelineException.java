package com.mentorlens.submission;

/** Thrown by the pipeline for a failure whose message is safe to show to the student. */
public class PipelineException extends RuntimeException {
    public PipelineException(String message) {
        super(message);
    }

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }
}
