package com.mentorlens.ai;

import com.mentorlens.runner.RunResult;
import java.util.Optional;

public interface AiClient {
    /** Empty when the AI is down, unconfigured, or its reply is invalid after one retry. */
    Optional<AiFeedback> review(String question, String code, String inputText, String expectedText, RunResult run);
}
