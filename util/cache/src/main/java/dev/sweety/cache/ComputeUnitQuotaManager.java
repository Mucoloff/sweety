package dev.sweety.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-tier Compute Unit (CU) Quota Manager.
 *
 * <p>Tracks rolling 5-hour usage, rolling weekly usage, and extra pre-paid credit balances.
 * Alerts at 80% (soft warning) and 95% (critical warning), triggering a soft-stop to {@code DEGRADED}
 * state at 100% exhaustion (zero negative debt).
 */
public final class ComputeUnitQuotaManager {

    public enum QuotaStatus {
        OK,
        WARNING_80,
        CRITICAL_95,
        EXHAUSTED_DEGRADED
    }

    public static final class UserQuota {
        private final long max5HourLimit;
        private final long maxWeeklyLimit;

        private long used5Hour;
        private long usedWeekly;
        private long extraCredits;
        private long windowStart5HourNanos;
        private long windowStartWeeklyNanos;

        public UserQuota(long max5HourLimit, long maxWeeklyLimit, long extraCredits) {
            this.max5HourLimit = max5HourLimit;
            this.maxWeeklyLimit = maxWeeklyLimit;
            this.extraCredits = extraCredits;
            long now = System.nanoTime();
            this.windowStart5HourNanos = now;
            this.windowStartWeeklyNanos = now;
        }

        public synchronized QuotaStatus consume(long units) {
            checkRoll();

            // Try 5-hour quota first
            if (used5Hour + units <= max5HourLimit && usedWeekly + units <= maxWeeklyLimit) {
                used5Hour += units;
                usedWeekly += units;
                return computeStatus();
            }

            // If 5h/weekly quota exceeded, fallback to extra pre-paid credits
            if (extraCredits >= units) {
                extraCredits -= units;
                return computeStatus();
            }

            // Zero debt: soft stop -> DEGRADED
            return QuotaStatus.EXHAUSTED_DEGRADED;
        }

        private QuotaStatus computeStatus() {
            double ratio5h = (double) used5Hour / max5HourLimit;
            double ratioWeekly = (double) usedWeekly / maxWeeklyLimit;
            double worst = Math.max(ratio5h, ratioWeekly);

            if (worst >= 0.95 && extraCredits <= 0) return QuotaStatus.CRITICAL_95;
            if (worst >= 0.80 && extraCredits <= 0) return QuotaStatus.WARNING_80;
            return QuotaStatus.OK;
        }

        private void checkRoll() {
            long now = System.nanoTime();
            // 5 hours = 5 * 3600 * 1_000_000_000 ns
            if (now - windowStart5HourNanos >= 18_000_000_000_000L) {
                windowStart5HourNanos = now;
                used5Hour = 0;
            }
            // 7 days = 7 * 86400 * 1_000_000_000 ns
            if (now - windowStartWeeklyNanos >= 604_800_000_000_000L) {
                windowStartWeeklyNanos = now;
                usedWeekly = 0;
            }
        }

        public synchronized long getUsed5Hour() { return used5Hour; }
        public synchronized long getUsedWeekly() { return usedWeekly; }
        public synchronized long getExtraCredits() { return extraCredits; }
        public synchronized void addExtraCredits(long credits) { this.extraCredits += credits; }
    }

    private final ConcurrentHashMap<Long, UserQuota> userQuotas = new ConcurrentHashMap<>();
    private final long default5hLimit;
    private final long defaultWeeklyLimit;

    public ComputeUnitQuotaManager(long default5hLimit, long defaultWeeklyLimit) {
        this.default5hLimit = default5hLimit;
        this.defaultWeeklyLimit = defaultWeeklyLimit;
    }

    public static ComputeUnitQuotaManager of(long default5hLimit, long defaultWeeklyLimit) {
        return new ComputeUnitQuotaManager(default5hLimit, defaultWeeklyLimit);
    }

    public QuotaStatus recordUsage(long userId, long units) {
        UserQuota q = userQuotas.computeIfAbsent(userId, id -> new UserQuota(default5hLimit, defaultWeeklyLimit, 0));
        return q.consume(units);
    }

    public void addCredits(long userId, long extraCredits) {
        UserQuota q = userQuotas.computeIfAbsent(userId, id -> new UserQuota(default5hLimit, defaultWeeklyLimit, 0));
        q.addExtraCredits(extraCredits);
    }

    public UserQuota getQuota(long userId) {
        return userQuotas.get(userId);
    }
}
