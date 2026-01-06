package dev.sweety.core.math.algo;

import dev.sweety.core.math.vector.stack.DequeStack;
import dev.sweety.core.math.vector.stack.LinkedStack;
import dev.sweety.core.math.vector.stack.Stack;
import dev.sweety.core.math.vector.stack.StackContext;

import java.util.function.Function;
import java.util.function.Supplier;

public class LinkedStackUtils {

    public static <F> Stack<F> newDequeStack() {
        return new DequeStack<>();
    }

    public static <F> Stack<F> newLinkedStack() {
        return new LinkedStack<>();
    }

    public static <F, R> R run(F initialFrame, Function<StackContext<F, R>, R> step) {
        return run(initialFrame, step, LinkedStackUtils::newDequeStack);
    }

    public static <F, R> R run(F initialFrame, Function<StackContext<F, R>, R> step,
                               Supplier<Stack<F>> stackFactory) {
        Stack<F> stack = stackFactory.get();
        stack.push(initialFrame);
        StackContext<F, R> ctx = new StackContext<>(stack);

        R result = null;
        while (!stack.isEmpty() && result == null) {
            F current = stack.pop();
            result = step.apply(ctx.with(current));
        }
        return result;
    }

    public static <F, R> R runWithStack(F initialFrame, Function<F, R> step) {
        return runWithStack(initialFrame, step, LinkedStackUtils::newDequeStack);
    }

    public static <F, R> R runWithStack(F initialFrame, Function<F, R> step,
                                        Supplier<Stack<F>> stackFactory) {
        Stack<F> stack = stackFactory.get();
        stack.push(initialFrame);
        R result = null;

        while (!stack.isEmpty() && result == null) {
            F frame = stack.peek();
            result = step.apply(frame);
            if (result != null) {
                stack.pop();
            }
        }
        return result;
    }

}
