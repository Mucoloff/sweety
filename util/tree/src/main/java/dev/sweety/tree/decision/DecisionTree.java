package dev.sweety.tree.decision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composite Decision Tree orchestrator executing evaluation branches in sequence.
 */
public class DecisionTree<C, R> {

    private final List<DecisionNode<C, R>> nodes = new ArrayList<>();

    public DecisionTree<C, R> addNode(DecisionNode<C, R> node) {
        if (node != null) {
            nodes.add(node);
        }
        return this;
    }

    public List<DecisionNode<C, R>> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public R evaluate(DecisionContext<C> context) {
        if (context == null) return null;
        for (DecisionNode<C, R> node : nodes) {
            R result = node.process(context);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public void clear() {
        nodes.clear();
    }
}
