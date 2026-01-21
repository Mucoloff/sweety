package dev.sweety.core.math.vector.deque;

import dev.sweety.core.math.vector.deque.stack.Stack;
import dev.sweety.core.math.vector.deque.stack.StackContext;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class StackRunner {

    private StackRunner() {
    }

    /* ================= DEFAULT ================= */

    public static <F, R> R run(
            F initialFrame,
            Function<StackContext<F>, R> step
    ) {
        return run(initialFrame, step, BlockingDeque::new);
    }

    public static <F, R> R runWithStack(
            F initialFrame,
            Function<F, R> step
    ) {
        return runWithStack(initialFrame, step, BlockingDeque::new);
    }

    /* ================= CONFIGURABLE ================= */

    public static <F, R> R run(
            F initialFrame,
            Function<StackContext<F>, R> step,
            Supplier<? extends Stack<F>> stackFactory
    ) {
        Objects.requireNonNull(step);
        Stack<F> stack = stackFactory.get();
        stack.push(initialFrame);

        StackContext<F> ctx = new StackContext<>(stack);

        while (!stack.isEmpty()) {
            R result = step.apply(ctx.with(stack.pop()));
            if (result != null) return result;
        }
        return null;
    }

    public static <F, R> R runWithStack(
            F initialFrame,
            Function<F, R> step,
            Supplier<? extends Stack<F>> stackFactory
    ) {
        Objects.requireNonNull(step);
        Stack<F> stack = stackFactory.get();
        stack.push(initialFrame);

        while (!stack.isEmpty()) {
            R result = step.apply(stack.peek());
            if (result != null) {
                stack.pop();
                return result;
            }
        }
        return null;
    }
}
