package dev.sweety.core.math.vector.deque.stack;

public final class StackContext<F> {
    private final Stack<F> stack;
    private F frame;

    public StackContext(Stack<F> stack) {
        this.stack = stack;
    }

    public StackContext<F> with(F frame) {
        this.frame = frame;
        return this;
    }

    public F frame() {
        return frame;
    }

    public void push(F frame) {
        stack.push(frame);
    }
}
