package dev.sweety.netty.feature;

import dev.sweety.core.thread.ThreadUtil;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;

import java.net.ConnectException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class AutoReconnect {

    private final ScheduledExecutorService thread = ThreadUtil.namedScheduler("auto-reconnect-thread");

    @Getter
    @Setter
    private long timeout;
    @Getter
    @Setter
    private TimeUnit timeUnit;
    private final Supplier<Channel> start;

    public AutoReconnect(long timeout, TimeUnit timeUnit, Supplier<Channel> start) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.start = start;
    }

    public void onQuit() {
        thread.schedule(start::get, timeout, timeUnit);
    }

    public void onException(Throwable t) {
        if (!(t instanceof ConnectException ex)) return;
        thread.schedule(start::get, timeout, timeUnit);
    }

    public void shutdown() {
        thread.shutdown();
    }
}
