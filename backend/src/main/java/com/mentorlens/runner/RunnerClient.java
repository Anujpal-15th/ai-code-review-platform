package com.mentorlens.runner;

public interface RunnerClient {
    /** Throws when the runner is unreachable, returns an HTTP error, or replies with nonsense. */
    RunResult run(String code, String inputText);
}
