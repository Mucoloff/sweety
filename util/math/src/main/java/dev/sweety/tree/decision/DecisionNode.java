package dev.sweety.tree.decision;

/**
 * Functional node interface for the hybrid decision tree.
 */
@FunctionalInterface
public interface DecisionNode<C, R> {

    /**
     * Evaluates context against this branch.
     * Returns a non-null result if handled, or null to pass downstream.
     */
    R process(DecisionContext<C> context);
}
