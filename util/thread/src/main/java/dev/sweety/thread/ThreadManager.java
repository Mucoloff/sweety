package dev.sweety.thread;

import dev.sweety.math.MathUtils;
import dev.sweety.math.RandomUtils;

import lombok.Getter;
import lombok.SneakyThrows;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

    @SneakyThrows
    public synchronized ProfileThread getAvailableProfileThread() {
        ProfileThread profileThread;

        if (this.profileThreads.size() < MAX_THREADS) this.profileThreads.add(profileThread = new ProfileThread(name));
        else profileThread = MathUtils.findBest(profileThreads, (a, b) -> a.getProfileCount() < b.getProfileCount(), RandomUtils.randomElement(this.profileThreads));

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
        MathUtils.parallel(this.profileThreads).forEach(this::shutdown);
    }
}