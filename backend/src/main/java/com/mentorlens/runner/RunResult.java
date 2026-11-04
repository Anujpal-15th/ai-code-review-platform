package com.mentorlens.runner;

import com.mentorlens.compare.Step;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Runner response (docs/api-contract.md, "Backend to Runner"). */
public record RunResult(String outcome, JsonNode output, String stdout, String error, List<Step> steps) {}
