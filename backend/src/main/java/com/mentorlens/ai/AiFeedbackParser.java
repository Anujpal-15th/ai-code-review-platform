package com.mentorlens.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Turns the model's text into a validated AiFeedback, or empty. */
public class AiFeedbackParser {
    private static final JsonMapper MAPPER = new JsonMapper();

    public static Optional<AiFeedback> parse(String text, int codeLines) {
        if (text == null) return Optional.empty();
        try {
            JsonNode root = MAPPER.readTree(stripFence(text.trim()));
            String correctCode = str(root.get("correctCode"));
            JsonNode ex = root.path("explanation");
            JsonNode cx = root.path("complexity");
            String what = str(ex.get("what")), where = str(ex.get("where")), why = str(ex.get("why"));
            String time = str(cx.get("time")), space = str(cx.get("space"));
            String mistakeType = str(root.get("mistakeType"));
            JsonNode h = root.get("hints");
            if (anyNull(correctCode, what, where, why, time, space, mistakeType)
                    || h == null || !h.isArray() || h.size() != 3) return Optional.empty();
            List<String> hints = new ArrayList<>();
            for (JsonNode n : h) {
                String s = str(n);
                if (s == null) return Optional.empty();
                hints.add(s);
            }
            List<AiFeedback.Issue> issues = new ArrayList<>();
            JsonNode in = root.get("issues");
            if (in == null || !in.isArray()) return Optional.empty();
            for (JsonNode i : in) {
                String type = str(i.get("type")), msg = str(i.get("message"));
                JsonNode line = i.get("line");
                if (line == null || !line.isInt() || line.asInt() < 1 || line.asInt() > codeLines || msg == null) continue;
                if (!"bug".equals(type) && !"style".equals(type)) continue;
                issues.add(new AiFeedback.Issue(line.asInt(), type, msg));
            }
            return Optional.of(new AiFeedback(correctCode, new AiFeedback.Explanation(what, where, why), hints,
                    new AiFeedback.Complexity(time, space), mistakeType, issues, !in.isEmpty() && issues.isEmpty()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean anyNull(String... v) {
        for (String s : v) if (s == null) return true;
        return false;
    }

    /** Non-blank text value, else null. NUL makes the whole reply invalid: PostgreSQL text cannot store it. */
    private static String str(JsonNode n) {
        if (n != null && n.isString() && n.asString().indexOf('\0') >= 0) throw new IllegalArgumentException("NUL");
        return n != null && n.isString() && !n.asString().isBlank() ? n.asString() : null;
    }

    private static String stripFence(String t) {
        if (!t.startsWith("```")) return t;
        int nl = t.indexOf('\n');
        int end = t.lastIndexOf("```");
        return nl < 0 || end <= nl ? t : t.substring(nl + 1, end).trim();
    }
}
