package com.mentorlens.compare;

import tools.jackson.databind.JsonNode;

/** One traced step from the runner (docs/api-contract.md, "Backend to Runner"). */
public record Step(int step, int line, String event, String function, JsonNode variables) {}
