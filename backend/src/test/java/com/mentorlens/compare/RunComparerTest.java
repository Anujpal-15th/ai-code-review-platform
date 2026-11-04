package com.mentorlens.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class RunComparerTest {

    static final ObjectMapper M = new ObjectMapper();
    final RunComparer comparer = new RunComparer();

    static List<Step> trace(String name) throws Exception {
        try (InputStream in = RunComparerTest.class.getResourceAsStream("/traces/" + name)) {
            return M.readValue(in, new TypeReference<List<Step>>() {});
        }
    }

    static Step step(int n, int line, String varsJson) {
        return new Step(n, line, "line", "f", M.readTree(varsJson));
    }

    @Test
    void sampleDiffersAtStep8Line9OnLeft() throws Exception {
        FirstDifference d = comparer.compare(trace("two_sum_wrong.json"), trace("two_sum_correct.json")).orElseThrow();
        assertEquals(8, d.step());
        assertEquals(9, d.line());
        assertEquals("left", d.variable());
        assertEquals(1, d.yours().asInt());
        assertEquals(0, d.correct().asInt());
    }

    @Test
    void identicalRunsEmpty() throws Exception {
        assertTrue(comparer.compare(trace("two_sum_correct.json"), trace("two_sum_correct.json")).isEmpty());
    }

    @Test
    void noSharedVariablesEmpty() {
        var a = List.of(step(1, 1, "{\"a\":1}"), step(2, 2, "{\"a\":2}"));
        var b = List.of(step(1, 1, "{\"b\":1}"), step(2, 2, "{\"b\":2}"));
        assertTrue(comparer.compare(a, b).isEmpty());
    }

    @Test
    void oneRunEndsEarlyReturnsLastStep() {
        var a = List.of(step(1, 1, "{\"x\":1}"), step(2, 2, "{\"x\":1}"));
        var b = List.of(step(1, 1, "{\"x\":1}"), step(2, 2, "{\"x\":1}"), step(3, 3, "{\"x\":1}"));
        FirstDifference d = comparer.compare(a, b).orElseThrow();
        assertEquals(2, d.step());
        assertEquals(2, d.line());
        assertNull(d.variable());
        assertNull(d.yours());
        assertNull(d.correct());
    }

    @Test
    void arrayValuesComparedAsJson() {
        var a = List.of(step(1, 1, "{\"x\":[1,2]}"), step(2, 2, "{\"x\":[1,2]}"));
        var b = List.of(step(1, 1, "{\"x\":[1,2]}"), step(2, 2, "{\"x\":[1,3]}"));
        FirstDifference d = comparer.compare(a, b).orElseThrow();
        assertEquals("x", d.variable());
        assertEquals("[1,3]", d.correct().toString());
    }

    @Test
    void variableOnlyInOneRunIsNotADifference() {
        var a = List.of(step(1, 1, "{\"x\":1}"), step(2, 2, "{\"x\":1,\"y\":5}"));
        var b = List.of(step(1, 1, "{\"x\":1}"), step(2, 2, "{\"x\":1}"));
        assertTrue(comparer.compare(a, b).isEmpty());
    }
}
