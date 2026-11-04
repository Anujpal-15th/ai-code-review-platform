package com.mentorlens.compare;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;

public class RunComparer {

    /**
     * Finds the first step where a variable present in both runs has a different value.
     * Steps are walked by index, variables in name order; JSON values are compared structurally.
     * The reported line is the previous step's line, since that line caused the change.
     * Returns empty if the runs are identical, or if fewer than half the variable names are shared
     * (shared names divided by all distinct names across both runs), since the runs are then
     * not comparable. If one run ends early with no difference so far, returns the shorter run's
     * last step with a null variable and null values.
     */
    public Optional<FirstDifference> compare(List<Step> yours, List<Step> correct) {
        if (yours.isEmpty() || correct.isEmpty() || sharedRatio(yours, correct) < 0.5) {
            return Optional.empty();
        }
        int common = Math.min(yours.size(), correct.size());
        for (int i = 0; i < common; i++) {
            Step a = yours.get(i);
            Step b = correct.get(i);
            Set<String> names = new TreeSet<>(names(a));
            names.retainAll(names(b)); // a variable that appears in only one run is not a difference
            for (String name : names) {
                JsonNode va = a.variables().get(name);
                JsonNode vb = b.variables().get(name);
                if (!va.equals(vb)) {
                    int line = i > 0 ? yours.get(i - 1).line() : a.line();
                    return Optional.of(new FirstDifference(a.step(), line, name, va, vb));
                }
            }
        }
        if (yours.size() == correct.size()) {
            return Optional.empty();
        }
        Step last = (yours.size() < correct.size() ? yours : correct).get(common - 1);
        return Optional.of(new FirstDifference(last.step(), last.line(), null, null, null));
    }

    private static double sharedRatio(List<Step> yours, List<Step> correct) {
        Set<String> a = allNames(yours);
        Set<String> b = allNames(correct);
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        a.retainAll(b);
        return all.isEmpty() ? 0 : (double) a.size() / all.size();
    }

    private static Set<String> allNames(List<Step> steps) {
        Set<String> out = new HashSet<>();
        steps.forEach(s -> out.addAll(names(s)));
        return out;
    }

    private static Set<String> names(Step s) {
        return s.variables() == null ? Set.of() : new HashSet<>(s.variables().propertyNames());
    }
}
