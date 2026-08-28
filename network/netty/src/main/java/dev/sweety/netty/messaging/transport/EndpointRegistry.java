package dev.sweety.netty.messaging.transport;

import dev.sweety.math.list.Long2ObjectConcurrentOpenHashMap;
import dev.sweety.math.list.Object2LongConcurrentOpenHashMap;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bidirectional {@code connectionId <-> UDP remote address} mapping for a UDP {@code Server}'s
 * connectionless peers — moved here from {@code server/session}'s {@code infra} package since it is
 * generic UDP-peer-registry plumbing, not session-local infra. UDP is connectionless — a client
 * proves it owns a live TCP session once (application-level handshake), and every subsequent
 * datagram from the same remote address is attributed back to that {@code connectionId} without
 * re-validating. A later re-handshake from a new address (NAT rebind) simply overwrites the mapping.
 *
 * <p>Per-connection replay guard: datagrams must carry a strictly increasing {@code seq}; anything
 * at or below the last seen value is dropped (best-effort — no retransmission, no reordering
 * tolerance needed for presence/ping snapshots).
 */
public final class EndpointRegistry {

    // No-boxing lock-striped maps (see dev.sweety.math.list) instead of ConcurrentHashMap<Long,...>/
    // <..., Long> — same "-1 = absent" sentinel connectionIdFor already returns, zero call-site change.
    private final Object2LongConcurrentOpenHashMap<InetSocketAddress> addressToConnection = Object2LongConcurrentOpenHashMap.create();
    private final Long2ObjectConcurrentOpenHashMap<InetSocketAddress> connectionToAddress = Long2ObjectConcurrentOpenHashMap.create();
    private final Long2ObjectConcurrentOpenHashMap<AtomicLong> lastSeq = Long2ObjectConcurrentOpenHashMap.create();

    /** Register (or move) the UDP endpoint owning {@code connectionId}. Idempotent. */
    public void register(long connectionId, InetSocketAddress remote) {
        InetSocketAddress previous = connectionToAddress.put(connectionId, remote);
        if (previous != null && !previous.equals(remote)) addressToConnection.removeLong(previous);
        addressToConnection.put(remote, connectionId);
        lastSeq.putIfAbsent(connectionId, new AtomicLong(-1));
    }

    /** The connectionId owning this remote address, or -1 if never registered (or evicted). */
    public long connectionIdFor(InetSocketAddress remote) {
        return addressToConnection.getLong(remote);
    }

    /** The live UDP endpoint for a connectionId, or null if it never registered (yet). */
    public InetSocketAddress endpointFor(long connectionId) {
        return connectionToAddress.get(connectionId);
    }

    /** True if {@code seq} is newer than the last one accepted for this connection (and records it). */
    public boolean acceptSeq(long connectionId, long seq) {
        AtomicLong last = lastSeq.get(connectionId);
        if (last == null) {
            AtomicLong created = new AtomicLong(-1);
            AtomicLong existing = lastSeq.putIfAbsent(connectionId, created);
            last = existing != null ? existing : created;
        }
        while (true) {
            long prev = last.get();
            if (seq <= prev) return false;
            if (last.compareAndSet(prev, seq)) return true;
        }
    }

    /** Drop the endpoint mapping for a closed TCP session (channel-close hook). */
    public void remove(long connectionId) {
        InetSocketAddress addr = connectionToAddress.remove(connectionId);
        if (addr != null) addressToConnection.remove(addr, connectionId);
        lastSeq.remove(connectionId);
    }
}
