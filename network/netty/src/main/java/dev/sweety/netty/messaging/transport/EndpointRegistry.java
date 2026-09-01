package dev.sweety.netty.messaging.transport;

import dev.sweety.math.list.Long2ObjectConcurrentOpenHashMap;
import dev.sweety.math.list.Object2LongConcurrentOpenHashMap;

import java.net.InetSocketAddress;

/**
 * Bidirectional {@code connectionId <-> UDP remote address} mapping for a UDP {@code Server}'s
 * connectionless peers. Supports NAT rebinding and sliding-window anti-replay sequence checking.
 */
public final class EndpointRegistry {

    private final Object2LongConcurrentOpenHashMap<InetSocketAddress> addressToConnection = Object2LongConcurrentOpenHashMap.create();
    private final Long2ObjectConcurrentOpenHashMap<InetSocketAddress> connectionToAddress = Long2ObjectConcurrentOpenHashMap.create();
    private final Long2ObjectConcurrentOpenHashMap<SlidingWindowSequenceGuard> guards = Long2ObjectConcurrentOpenHashMap.create();

    /** Register (or move) the UDP endpoint owning {@code connectionId}. Idempotent. */
    public void register(long connectionId, InetSocketAddress remote) {
        InetSocketAddress previous = connectionToAddress.put(connectionId, remote);
        if (previous != null && !previous.equals(remote)) addressToConnection.removeLong(previous);
        addressToConnection.put(remote, connectionId);
        guards.putIfAbsent(connectionId, new SlidingWindowSequenceGuard());
    }

    /** The connectionId owning this remote address, or -1 if never registered (or evicted). */
    public long connectionIdFor(InetSocketAddress remote) {
        return addressToConnection.getLong(remote);
    }

    /** The live UDP endpoint for a connectionId, or null if it never registered (yet). */
    public InetSocketAddress endpointFor(long connectionId) {
        return connectionToAddress.get(connectionId);
    }

    /** True if {@code seq} is acceptable within the 64-frame sliding window (and records it). */
    public boolean acceptSeq(long connectionId, long seq) {
        SlidingWindowSequenceGuard guard = guards.get(connectionId);
        if (guard == null) {
            SlidingWindowSequenceGuard created = new SlidingWindowSequenceGuard();
            SlidingWindowSequenceGuard existing = guards.putIfAbsent(connectionId, created);
            guard = existing != null ? existing : created;
        }
        return guard.accept(seq);
    }

    /** Drop the endpoint mapping for a closed session. */
    public void remove(long connectionId) {
        InetSocketAddress addr = connectionToAddress.remove(connectionId);
        if (addr != null) addressToConnection.remove(addr, connectionId);
        guards.remove(connectionId);
    }
}
