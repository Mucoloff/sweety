package dev.sweety.cache;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * High-performance, zero-boxing Compute Unit (CU) debit accumulator.
 *
 * <p>Collects local entity consumption debits in a primitive {@link Long2IntOpenHashMap}
 * without allocating {@code Long} or {@code Integer} wrappers.
 * Flushed periodically (e.g. every 5s heartbeat) to the persistent / Redis store.
 */
public final class FastutilDebitAccumulator {

    private final Long2IntOpenHashMap pendingDebits = new Long2IntOpenHashMap();

    public FastutilDebitAccumulator() {
        pendingDebits.defaultReturnValue(0);
    }

    /**
     * Records a debit for the given primitive user ID.
     *
     * @param userId primitive user ID
     * @param units Compute Units consumed
     */
    public synchronized void recordDebit(long userId, int units) {
        if (units <= 0) return;
        pendingDebits.addTo(userId, units);
    }

    /**
     * Gets the current accumulated debit for an entity without flushing.
     */
    public synchronized int getPending(long userId) {
        return pendingDebits.get(userId);
    }

    /**
     * Drains all pending debits and passes them to the consumer, resetting the local buffer in O(1).
     *
     * @param consumer action receiving (userId, totalUnitsToDebit)
     */
    public synchronized void drain(BiConsumer<Long, Integer> consumer) {
        Objects.requireNonNull(consumer, "consumer must not be null");
        if (pendingDebits.isEmpty()) return;

        for (var entry : pendingDebits.long2IntEntrySet()) {
            consumer.accept(entry.getLongKey(), entry.getIntValue());
        }
        pendingDebits.clear();
    }

    public synchronized boolean isEmpty() {
        return pendingDebits.isEmpty();
    }

    public synchronized int size() {
        return pendingDebits.size();
    }
}
