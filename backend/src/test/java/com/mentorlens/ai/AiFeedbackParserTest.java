package com.mentorlens.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiFeedbackParserTest {
    static final String VALID = """
            {"correctCode":"def f(): pass",
             "explanation":{"what":"w","where":"x","why":"y"},
             "hints":["a","b","c"],
             "complexity":{"time":"O(n)","space":"O(1)"},
             "mistakeType":"off-by-one",
             "issues":[{"line":2,"type":"bug","message":"m"}]}""";

    @Test
    void validJsonParses() {
        var fb = AiFeedbackParser.parse(VALID, 10).orElseThrow();
        assertThat(fb.hints()).containsExactly("a", "b", "c");
        assertThat(fb.explanation().why()).isEqualTo("y");
        assertThat(fb.complexity().time()).isEqualTo("O(n)");
        assertThat(fb.issues()).hasSize(1);
    }

    @Test
    void fencedJsonParses() {
        assertThat(AiFeedbackParser.parse("```json\n" + VALID + "\n```", 10)).isPresent();
    }

    @Test
    void missingHintsRejected() {
        assertThat(AiFeedbackParser.parse(VALID.replace("\"hints\":[\"a\",\"b\",\"c\"]", "\"hints\":[\"a\",\"b\"]"), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("\"hints\":[\"a\",\"b\",\"c\"],", ""), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("[\"a\",\"b\",\"c\"]", "[\"a\",\" \",\"c\"]"), 10)).isEmpty();
    }

    @Test
    void blankRequiredFieldsRejected() {
        assertThat(AiFeedbackParser.parse(VALID.replace("\"mistakeType\":\"off-by-one\"", "\"mistakeType\":\"\""), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("\"space\":\"O(1)\"", "\"space\":\" \""), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("\"where\":\"x\"", "\"where\":null"), 10)).isEmpty();
    }

    @Test
    void issueOnMissingLineDropped() {
        String json = VALID.replace("\"issues\":[{\"line\":2,\"type\":\"bug\",\"message\":\"m\"}]",
                "\"issues\":[{\"type\":\"bug\",\"message\":\"no line\"},{\"line\":3,\"type\":\"style\",\"message\":\"ok\"}]");
        var fb = AiFeedbackParser.parse(json, 10).orElseThrow();
        assertThat(fb.issues()).extracting(AiFeedback.Issue::line).containsExactly(3);
    }

    @Test
    void issueBeyondCodeEndDropped() {
        // VALID has an issue on line 2
        assertThat(AiFeedbackParser.parse(VALID, 2).orElseThrow().issues()).hasSize(1);
        assertThat(AiFeedbackParser.parse(VALID, 1).orElseThrow().issues()).isEmpty();
    }

    @Test
    void issueWithUnknownTypeDropped() {
        String json = VALID.replace("\"type\":\"bug\"", "\"type\":\"security\"");
        assertThat(AiFeedbackParser.parse(json, 10).orElseThrow().issues()).isEmpty();
    }

    private static final String ISSUES = "\"issues\":[{\"line\":2,\"type\":\"bug\",\"message\":\"m\"}]";

    @Test
    void missingOrNonArrayIssuesRejected() {
        assertThat(AiFeedbackParser.parse(VALID.replace(ISSUES, "\"x\":1"), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace(ISSUES, "\"issues\":null"), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace(ISSUES, "\"issues\":\"none\""), 10)).isEmpty();
    }

    @Test
    void emptyIssuesIsValidAndNotDropped() {
        var fb = AiFeedbackParser.parse(VALID.replace(ISSUES, "\"issues\":[]"), 10).orElseThrow();
        assertThat(fb.issues()).isEmpty();
        assertThat(fb.issuesAllDropped()).isFalse();
    }

    @Test
    void allIssuesDroppedIsFlaggedButSomeDroppedIsNot() {
        assertThat(AiFeedbackParser.parse(VALID, 1).orElseThrow().issuesAllDropped()).isTrue();
        String mixed = VALID.replace("\"issues\":[", "\"issues\":[{\"line\":99,\"type\":\"bug\",\"message\":\"x\"},");
        assertThat(AiFeedbackParser.parse(mixed, 10).orElseThrow().issuesAllDropped()).isFalse();
    }

    @Test
    void nulInAnyAiTextRejected() {
        String nul = "\\u0000";
        assertThat(AiFeedbackParser.parse(VALID.replace("def f(): pass", "def f():" + nul), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("\"what\":\"w\"", "\"what\":\"w" + nul + "\""), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("\"a\"", "\"a" + nul + "\""), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("off-by-one", "off" + nul), 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("\"message\":\"m\"", "\"message\":\"m" + nul + "\""), 10)).isEmpty();
    }

    @Test
    void garbageReturnsEmpty() {
        assertThat(AiFeedbackParser.parse("not json", 10)).isEmpty();
        assertThat(AiFeedbackParser.parse(null, 10)).isEmpty();
    }
}
