package com.mentorlens.compare;

import tools.jackson.databind.JsonNode;

/** variable, yours and correct are null when one run simply ended before the other. */
public record FirstDifference(int step, int line, String variable, JsonNode yours, JsonNode correct) {}
