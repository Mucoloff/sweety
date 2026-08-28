package dev.sweety.thread;

import dev.sweety.math.MathUtils;
import dev.sweety.math.RandomUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ThreadManager {

    private static final int MAX_THREADS_PER_TYPE = Runtime.getRuntime().availableProcessors() * 2;

    private final Map<ThreadType, List<ProfileThread>> profileThreads = new ConcurrentHashMap<>();

    private final String name;

    public ThreadManager(final String name) {
        this.name = name;
    }

    public ThreadManager() {
        this("profile-thread");
    }

    public Map<ThreadType, List<ProfileThread>> getProfileThreads() {
        return profileThreads;
    }

    // ── pool-based allocation ─────────────────────────────────────────────────

    private final MathUtils.Compare<ProfileThread> comparator =
            MathUtils.Compare.min(Comparator.comparingInt(ProfileThread::getProfileCount));

    /**
     * Returns the least-loaded existing {@link ProfileThread} for {@code type},
     * creating a new one (up to {@value #MAX_THREADS_PER_TYPE} per type) if needed.
     * Increments the thread's profile-count so callers should decrement when done
     * (or use {@link #fireAndForget} which handles this automatically).
     */
    public synchronized ProfileThread getAvailableProfileThread(String name, ThreadType type) {
        final List<ProfileThread> threads =
                this.profileThreads.computeIfAbsent(type, k -> new ArrayList<>());

        final ProfileThread profileThread;
        if (threads.size() < MAX_THREADS_PER_TYPE) {
            threads.add(profileThread = new ProfileThread(name, type));
        } else {
            profileThread = MathUtils.findBest(threads, comparator, RandomUtils.randomElement(threads));
        }

        if (profileThread == null)
            throw new IllegalStateException("ThreadManager returned a null profile thread for type " + type);

        return profileThread.incrementAndGet();
    }

    public synchronized ProfileThread getAvailableProfileThread(ThreadType type) {
        return getAvailableProfileThread(this.name, type);
    }

    public ProfileThread getAvailableProfileThread() {
        return getAvailableProfileThread(ThreadType.SINGLE);
    }

    // ── dedicated (non-pooled) threads ────────────────────────────────────────

    /**
     * Creates a dedicated {@link ProfileThread} that is <em>not</em> tracked in the pool.
     * Use this for long-lived, named threads (I/O workers, autosave, etc.) where you own
     * the full lifecycle. Call {@link ProfileThread#shutdown()} to terminate.
     */
    public ProfileThread createDedicated(String name, ThreadType type) {
        return new ProfileThread(name, type);
    }

    public ProfileThread createDedicated(String name) {
        return createDedicated(name, ThreadType.SINGLE);
    }

    // ── fire-and-forget ───────────────────────────────────────────────────────

    public <T> CompletableFuture<T> fireAndForget(final ThreadType type,
                                                   final Function<ProfileThread, CompletableFuture<T>> action) {
        final ProfileThread thread = getAvailableProfileThread(type);
        final CompletableFuture<T> future = action.apply(thread);
        future.whenComplete((result, error) -> thread.decrement());
        return future;
    }

    public <T> CompletableFuture<T> fireAndForget(final Function<ProfileThread, CompletableFuture<T>> action) {
        return fireAndForget(ThreadType.SINGLE, action);
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Decrements the profile-count for {@code profileThread}.
     * Shuts it down and removes it from the pool when the count reaches zero.
     */
    public synchronized void shutdown(final ProfileThread profileThread) {
        if (profileThread == null) return;
        if (profileThread.decrement() > 0) return; // still in use by other callers

        for (List<ProfileThread> threads : profileThreads.values()) {
            if (threads.remove(profileThread)) {
                profileThread.shutdown();
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
