package dev.sweety.thread;

import dev.sweety.math.MathUtils;
import dev.sweety.math.RandomUtils;

import lombok.Getter;
import lombok.SneakyThrows;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class ThreadManager {

    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    @Getter
    private final List<ProfileThread> profileThreads = new CopyOnWriteArrayList<>();

    private final String name;

    public ThreadManager(final String name) {
        this.name = name;
    }

    public ThreadManager() {
        this("profile-thread");
    }

    public <T> CompletableFuture<T> fireAndForget(final Function<ProfileThread, CompletableFuture<T>> action) {
        final ProfileThread thread = getAvailableProfileThread();
        final CompletableFuture<T> future = action.apply(thread);
        thread.decrement();
        return future;
    }

    private final MathUtils.Compare<ProfileThread> comparator = MathUtils.Compare.min(Comparator.comparingInt(ProfileThread::getProfileCount));

    @SneakyThrows
    public synchronized ProfileThread getAvailableProfileThread() {
        final ProfileThread profileThread;

        if (this.profileThreads.size() < MAX_THREADS)
            this.profileThreads.add(profileThread = new ProfileThread(this.name));
        else
            profileThread = MathUtils.findBest(profileThreads, comparator, RandomUtils.randomElement(this.profileThreads));

        if (profileThread == null)
            throw new Exception("Encountered a null profile thread, Please restart the server to avoid any issues.");

        return profileThread.incrementAndGet();
    }

    public synchronized void shutdown(final ProfileThread profileThread) {
        if (profileThread == null) return;
        if (profileThread.decrement() <= 0) return;
        if (!this.profileThreads.contains(profileThread)) return;
        this.profileThreads.remove(profileThread.shutdown());
    }

    public void shutdown() {
        MathUtils.parallel(profileThreads).forEach(this::shutdown);
    }
}