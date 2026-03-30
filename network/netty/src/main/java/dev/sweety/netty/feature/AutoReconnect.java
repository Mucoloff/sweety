package dev.sweety.netty.feature;

import dev.sweety.thread.ProfileThread;
import io.netty.channel.Channel;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class AutoReconnect {

    private final ProfileThread thread = new ProfileThread("auto-reconnect-thread");

    private long timeout;
    private TimeUnit timeUnit;

    private final Supplier<Channel> start;
    private CompletableFuture<Channel> task;

    public AutoReconnect(long timeout, TimeUnit timeUnit, Supplier<Channel> start) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.start = start;
    }

    public static boolean exception(Throwable t) {
        return checkInstance(t) || checkMsg(t);
    }

    public void complete() {
        if (task != null) task.cancel(true);
    }

    public void onQuit() {
        start();
    }

    private void start() {
        complete();
        this.task = thread.scheduleWithFixedDelay(start::get, timeout, timeout, timeUnit);
    }

    public boolean onException(Throwable t) {
        if (exception(t)) {
            start();
            return true;
        }
        return false;
    }

    public static boolean checkInstance(Throwable t) {
        return t instanceof ClosedChannelException
                || t instanceof SocketException
                || t instanceof SocketTimeoutException
                || t instanceof EOFException
                || (t instanceof CompletionException c && checkInstance(c.getCause()))
                ;
    }

    public static boolean checkMsg(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("connection reset") || m.contains("broken pipe") || m.contains("connection refused");
    }

    public void shutdown() {
        thread.shutdown();
    }

    public long timeout() {
        return timeout;
    }

    public AutoReconnect setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public TimeUnit timeUnit() {
        return timeUnit;
    }

    public AutoReconnect setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
        return this;
    }
}