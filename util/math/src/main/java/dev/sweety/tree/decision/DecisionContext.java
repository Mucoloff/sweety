package dev.sweety.tree.decision;

import java.util.List;
import java.util.Map;

/**
 * Generic contextual container passed through the Hybrid Decision Tree.
 */
public record DecisionContext<T>(
        String rawInput,
        String normalizedInput,
        List<String> tokens,
        Map<String, Float> queryVector,
        T customData
) {
    public static <T> DecisionContext<T> of(String rawInput, String normalizedInput, List<String> tokens, Map<String, Float> queryVector, T customData) {
        return new DecisionContext<>(rawInput, normalizedInput, tokens, queryVector, customData);
    }
}
