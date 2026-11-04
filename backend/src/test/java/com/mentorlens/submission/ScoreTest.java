package com.mentorlens.submission;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorlens.ai.AiFeedback.Issue;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreTest {
    private static final Issue BUG = new Issue(1, "bug", "m");
    private static final Issue STYLE = new Issue(1, "style", "m");

    @Test
    void noIssuesIsFullMarks() {
        assertThat(Score.score(List.of())).isEqualTo(100);
    }

    @Test
    void manyBugsStopAtZero() {
        assertThat(Score.score(Collections.nCopies(50, BUG))).isZero();
    }

    @Test
    void bugLowersItMoreThanStyle() {
        assertThat(Score.score(List.of(BUG))).isEqualTo(85);
        assertThat(Score.score(List.of(STYLE))).isEqualTo(95);
    }

    @Test
    void weightsAddUp() {
        assertThat(Score.score(List.of(BUG, BUG, STYLE))).isEqualTo(65);
    }
}
