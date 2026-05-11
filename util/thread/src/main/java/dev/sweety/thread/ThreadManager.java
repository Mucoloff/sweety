package dev.sweety.thread;

import dev.sweety.math.MathUtils;
import dev.sweety.math.RandomUtils;

import lombok.Getter;
import lombok.SneakyThrows;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class ThreadManager {

    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    @Getter
    private final Map<ThreadType, List<ProfileThread>> profileThreads = new ConcurrentHashMap<>();

    private final String name;

    public ThreadManager(final String name) {
        this.name = name;
    }

    public ThreadManager() {
        this("profile-thread");
    }

    public <T> CompletableFuture<T> fireAndForget(final ThreadType type, final Function<ProfileThread, CompletableFuture<T>> action) {
        final ProfileThread thread = getAvailableProfileThread(type);
        final CompletableFuture<T> future = action.apply(thread);
        future.whenComplete((_, _) -> thread.decrement());
        return future;
    }

    public <T> CompletableFuture<T> fireAndForget(final Function<ProfileThread, CompletableFuture<T>> action) {
        return fireAndForget(ThreadType.SINGLE, action);
    }

    private final MathUtils.Compare<ProfileThread> comparator = MathUtils.Compare.min(Comparator.comparingInt(ProfileThread::getProfileCount));

    @SneakyThrows
    public synchronized ProfileThread getAvailableProfileThread(ThreadType type) {
        final List<ProfileThread> threads = this.profileThreads.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        final ProfileThread profileThread;

        if (threads.size() < MAX_THREADS) {
            threads.add(profileThread = new ProfileThread(this.name, type));
        } else {
            profileThread = MathUtils.findBest(threads, comparator, RandomUtils.randomElement(threads));
        }

        if (profileThread == null)
            throw new Exception("Encountered a null profile thread, Please restart the server to avoid any issues.");

        return profileThread.incrementAndGet();
    }

    public ProfileThread getAvailableProfileThread() {
        return getAvailableProfileThread(ThreadType.SINGLE);
    }

    public synchronized void shutdown(final ProfileThread profileThread) {
        if (profileThread == null) return;
        if (profileThread.decrement() <= 0) return;
        
        for (List<ProfileThread> threads : profileThreads.values()) {
            if (threads.contains(profileThread)) {
                threads.remove(profileThread.shutdown());
                break;
            }
        }
    }

    public void shutdown() {
        profileThreads.values().forEach(threads -> 
            MathUtils.parallel(threads).forEach(this::shutdown)
        );
    }
}