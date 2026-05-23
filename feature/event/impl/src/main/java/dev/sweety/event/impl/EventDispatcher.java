package dev.sweety.event.impl;

import dev.sweety.event.api.*;
import dev.sweety.event.api.function.Operation;
import dev.sweety.thread.ThreadManager;
import dev.sweety.thread.ThreadType;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

class EventDispatcher {

    private final ThreadManager threadManager;
    private final Executor asyncExecutor;
    private final CallbackRegistry registry;
    private final ExecutionPlanner planner;

    EventDispatcher(ThreadManager threadManager, Executor asyncExecutor,
                    CallbackRegistry registry, ExecutionPlanner planner) {
        this.threadManager = threadManager;
        this.asyncExecutor = asyncExecutor;
        this.registry = registry;
        this.planner = planner;
    }

    <T extends Event<?>> T dispatch(@NotNull T event) {
        if (event instanceof CancellableEvent<?> me) me.uncancel();

        List<ExecutionPlanner.ExecutionStep<T>> plan = planner.planFor(event.getClass(), registry);

        if (plan.isEmpty()) return event;

        final int initialHash = event.hashCode();

        for (ExecutionPlanner.ExecutionStep<T> step : plan) {
            step.run(event, this);
            if (isCancelled(event)) break;
        }

        if (initialHash != event.hashCode()) {
            if (event instanceof AbstractEvent<?> ae) {
                ae.setChanged(true);
            }
        }

        return event;
    }

    <T extends MutableEvent<?>, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Function<T, Object[]> changedArgsMapper,
            Object... args) {

        final T e = dispatch(event);

        if (isCancelled(e)) return Pair.of(e, null);

        R call = original.call(e.isChanged() ? changedArgsMapper.apply(e) : args);

        //noinspection unchecked
        final T post = (T) dispatch(e.post());

        return Pair.of(post, call);
    }

    <T extends Event<?>, R> Pair<T, R> dispatchWrapped(
            T event,
            Operation<R> original,
            Object... args) {

        final T e = dispatch(event);

        if (isCancelled(e)) return Pair.of(e, null);

        R call = original.call(args);

        //noinspection unchecked
        final T post = (T) dispatch(e.post());

        return Pair.of(post, call);
    }

    <T extends Event<?>> void executeSequential(EventCallback<T> cb, T event) {
        if (shouldCall(cb, event)) {
            if (cb.readOnly()) {
                T copy = wrapImmutable(event);
                cb.listener().call(copy);
                if (isCancelled(copy)) cancel(event);
            } else {
                cb.listener().call(event);
            }
        }
    }

    <T extends Event<?>> void executeParallel(List<EventCallback<T>> group, T event) {
        if (group.isEmpty()) return;

        if (group.size() == 1) {
            executeSequential(group.getFirst(), event);
            return;
        }

        T immutableEvent = wrapImmutable(event);

        CompletableFuture<?>[] futures = new CompletableFuture[group.size()];
        int idx = 0;
        Executor executor = asyncExecutor;

        if (executor != null) {
            for (EventCallback<T> cb : group) {
                if (shouldCall(cb, event)) {
                    futures[idx++] = CompletableFuture.runAsync(() -> cb.listener().call(immutableEvent), executor);
                }
            }
        } else {
            for (EventCallback<T> cb : group) {
                if (shouldCall(cb, event)) {
                    futures[idx++] = threadManager.fireAndForget(ThreadType.CACHED, t ->
                            t.execute(() -> cb.listener().call(immutableEvent))
                    );
                }
            }
        }

        if (idx > 0) {
            CompletableFuture.allOf(idx == futures.length ? futures : Arrays.copyOf(futures, idx)).join();
            if (isCancelled(immutableEvent)) {
                cancel(event);
            }
        }
    }

    boolean shouldCall(EventCallback<?> cb, Event<?> event) {
        return switch (cb.state()) {
            case BOTH -> true;
            case PRE -> event.isPre();
            case POST -> event.isPost();
        };
    }

    @SuppressWarnings("unchecked")
    <T extends Event<?>> T wrapImmutable(T event) {
        if (event instanceof MutableEvent<?> me) {
            return (T) me.toImmutable();
        }
        return event;
    }

    boolean isCancelled(Event<?> event) {
        if (event instanceof CancellableEvent<?> ce) {
            return ce.isCancelled();
        }
        return false;
    }

    void cancel(Event<?> event) {
        if (event instanceof CancellableEvent<?> ce) {
            ce.cancel();
        }
    }
}
