package dev.sweety.core.math.vector.stack;

public class StackContext<F, R> {
    private final Stack<F> stack;
    private F current;

    public StackContext(Stack<F> stack) {
        this.stack = stack;
    }

    public StackContext<F, R> with(F frame) {
        this.current = frame;
        return this;
    }

    public F frame() {
        return current;
    }

    public void push(F frame) {
        stack.push(frame);
    }
}
