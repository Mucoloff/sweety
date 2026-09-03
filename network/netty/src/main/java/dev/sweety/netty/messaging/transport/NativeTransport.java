package dev.sweety.netty.messaging.transport;

import dev.sweety.util.logger.SimpleLogger;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.ThreadFactory;

/**
 * Intelligent native transport selector for Netty.
 * Auto-detects and activates Linux {@link Epoll} or macOS {@link KQueue}, falling back to {@link io.netty.channel.nio.NioEventLoopGroup}.
 */
public final class NativeTransport {

    private static final SimpleLogger LOGGER = SimpleLogger.of("native-transport");

    public enum Type {
        EPOLL("Linux Epoll (Native)"),
        KQUEUE("macOS KQueue (Native)"),
        NIO("Java NIO (Standard)");

        private final String description;

        Type(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private static final Type ACTIVE_TYPE;

    static {
        Type detectedType = Type.NIO;
        try {
            Class<?> epollClass = Class.forName("io.netty.channel.epoll.Epoll");
            java.lang.reflect.Method isAvailableMethod = epollClass.getMethod("isAvailable");
            if ((boolean) isAvailableMethod.invoke(null)) {
                detectedType = Type.EPOLL;
            }
        } catch (Throwable ignored) {}

        if (detectedType == Type.NIO) {
            try {
                Class<?> kqueueClass = Class.forName("io.netty.channel.kqueue.KQueue");
                java.lang.reflect.Method isAvailableMethod = kqueueClass.getMethod("isAvailable");
                if ((boolean) isAvailableMethod.invoke(null)) {
                    detectedType = Type.KQUEUE;
                }
            } catch (Throwable ignored) {}
        }

        ACTIVE_TYPE = detectedType;
        LOGGER.info("Active native network transport: {}", ACTIVE_TYPE.description());
    }

    private NativeTransport() {}

    public static Type type() {
        return ACTIVE_TYPE;
    }

    public static boolean isNative() {
        return ACTIVE_TYPE != Type.NIO;
    }

    public static EventLoopGroup newEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        return switch (ACTIVE_TYPE) {
            case EPOLL -> new EpollEventLoopGroup(nThreads, threadFactory);
            case KQUEUE -> new KQueueEventLoopGroup(nThreads, threadFactory);
            case NIO -> new NioEventLoopGroup(nThreads, threadFactory);
        };
    }

    public static Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return switch (ACTIVE_TYPE) {
            case EPOLL -> EpollServerSocketChannel.class;
            case KQUEUE -> KQueueServerSocketChannel.class;
            case NIO -> NioServerSocketChannel.class;
        };
    }

    public static Class<? extends SocketChannel> socketChannelClass() {
        return switch (ACTIVE_TYPE) {
            case EPOLL -> EpollSocketChannel.class;
            case KQUEUE -> KQueueSocketChannel.class;
            case NIO -> NioSocketChannel.class;
        };
    }

    public static Class<? extends DatagramChannel> datagramChannelClass() {
        return switch (ACTIVE_TYPE) {
            case EPOLL -> EpollDatagramChannel.class;
            case KQUEUE -> KQueueDatagramChannel.class;
            case NIO -> NioDatagramChannel.class;
        };
    }
}
