package com.mentorlens.submission;

import com.mentorlens.ai.AiFeedback.Issue;
import java.util.List;

final class Score {
    private static final int BUG_WEIGHT = 15;
    private static final int STYLE_WEIGHT = 5;

    private Score() {}

    static int score(List<Issue> issues) {
        int total = 0;
        for (Issue i : issues) {
            total += "bug".equals(i.type()) ? BUG_WEIGHT : STYLE_WEIGHT; // the parser only lets these two through
        }
        return Math.max(0, 100 - total);
    }
}
