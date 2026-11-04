package com.mentorlens.ai;

import java.util.List;

public record AiFeedback(String correctCode, Explanation explanation, List<String> hints,
                         Complexity complexity, String mistakeType, List<Issue> issues) {
    public record Explanation(String what, String where, String why) {}
    public record Complexity(String time, String space) {}
    public record Issue(int line, String type, String message) {}
}
