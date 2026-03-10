package dev.sweety.netty.feature;

import dev.sweety.core.thread.ProfileThread;
import dev.sweety.record.annotations.DataIgnore;
import dev.sweety.record.annotations.RecordData;
import dev.sweety.record.annotations.Setter.Type;
import io.netty.channel.Channel;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@RecordData(setterTypes = Type.BUILDER_FLUENT)
public class AutoReconnect {

    @DataIgnore
    private final ProfileThread thread = new ProfileThread("auto-reconnect-thread");

    private long timeout;
    private TimeUnit timeUnit;

    @DataIgnore
    private final Supplier<Channel> start;
    @DataIgnore
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
        return switch (t) {
            case ConnectTimeoutException b -> true;
            case ConnectException a -> true;
            case ClosedChannelException c -> true;
            case SocketException d -> true;
            case SocketTimeoutException e -> true;
            case EOFException f -> true;
            case ReadTimeoutException g -> true;
            case WriteTimeoutException h -> true;
            case null, default -> false;
        };
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
}