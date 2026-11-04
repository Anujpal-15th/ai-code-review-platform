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
        var fb = AiFeedbackParser.parse(VALID).orElseThrow();
        assertThat(fb.hints()).containsExactly("a", "b", "c");
        assertThat(fb.explanation().why()).isEqualTo("y");
        assertThat(fb.complexity().time()).isEqualTo("O(n)");
        assertThat(fb.issues()).hasSize(1);
    }

    @Test
    void fencedJsonParses() {
        assertThat(AiFeedbackParser.parse("```json\n" + VALID + "\n```")).isPresent();
    }

    @Test
    void missingHintsRejected() {
        assertThat(AiFeedbackParser.parse(VALID.replace("\"hints\":[\"a\",\"b\",\"c\"]", "\"hints\":[\"a\",\"b\"]"))).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("\"hints\":[\"a\",\"b\",\"c\"],", ""))).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("[\"a\",\"b\",\"c\"]", "[\"a\",\" \",\"c\"]"))).isEmpty();
    }

    @Test
    void blankRequiredFieldsRejected() {
        assertThat(AiFeedbackParser.parse(VALID.replace("\"mistakeType\":\"off-by-one\"", "\"mistakeType\":\"\""))).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("\"space\":\"O(1)\"", "\"space\":\" \""))).isEmpty();
        assertThat(AiFeedbackParser.parse(VALID.replace("\"where\":\"x\"", "\"where\":null"))).isEmpty();
    }

    @Test
    void issueOnMissingLineDropped() {
        String json = VALID.replace("\"issues\":[{\"line\":2,\"type\":\"bug\",\"message\":\"m\"}]",
                "\"issues\":[{\"type\":\"bug\",\"message\":\"no line\"},{\"line\":3,\"type\":\"style\",\"message\":\"ok\"}]");
        var fb = AiFeedbackParser.parse(json).orElseThrow();
        assertThat(fb.issues()).extracting(AiFeedback.Issue::line).containsExactly(3);
    }

    @Test
    void issueWithUnknownTypeDropped() {
        String json = VALID.replace("\"type\":\"bug\"", "\"type\":\"security\"");
        assertThat(AiFeedbackParser.parse(json).orElseThrow().issues()).isEmpty();
    }

    @Test
    void garbageReturnsEmpty() {
        assertThat(AiFeedbackParser.parse("not json")).isEmpty();
        assertThat(AiFeedbackParser.parse(null)).isEmpty();
    }
}
