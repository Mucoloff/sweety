package dev.sweety.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputeUnitQuotaManagerTest {

    @Test
    void testNormalConsumptionWithin5HourAndWeeklyLimits() {
        ComputeUnitQuotaManager manager = ComputeUnitQuotaManager.of(1000L, 5000L);
        long userId = 101L;

        // 500 CUs -> 50% of 5h limit -> OK
        ComputeUnitQuotaManager.QuotaStatus status = manager.recordUsage(userId, 500L);
        assertEquals(ComputeUnitQuotaManager.QuotaStatus.OK, status);
        assertEquals(500L, manager.getQuota(userId).getUsed5Hour());
        assertEquals(500L, manager.getQuota(userId).getUsedWeekly());
    }

    @Test
    void testPreAlertAt80Percent() {
        ComputeUnitQuotaManager manager = ComputeUnitQuotaManager.of(1000L, 5000L);
        long userId = 102L;

        // 800 CUs -> exactly 80% of 5h limit -> WARNING_80
        ComputeUnitQuotaManager.QuotaStatus status = manager.recordUsage(userId, 800L);
        assertEquals(ComputeUnitQuotaManager.QuotaStatus.WARNING_80, status);
    }

    @Test
    void testCriticalAlertAt95Percent() {
        ComputeUnitQuotaManager manager = ComputeUnitQuotaManager.of(1000L, 5000L);
        long userId = 103L;

        // 950 CUs -> exactly 95% of 5h limit -> CRITICAL_95
        ComputeUnitQuotaManager.QuotaStatus status = manager.recordUsage(userId, 950L);
        assertEquals(ComputeUnitQuotaManager.QuotaStatus.CRITICAL_95, status);
    }

    @Test
    void testExhaustionWithoutDebtTriggersDegraded() {
        ComputeUnitQuotaManager manager = ComputeUnitQuotaManager.of(1000L, 5000L);
        long userId = 104L;

        // Consume full 1000
        manager.recordUsage(userId, 1000L);

        // Attempting to consume 1 extra unit with 0 extra credits -> EXHAUSTED_DEGRADED (No negative debt)
        ComputeUnitQuotaManager.QuotaStatus status = manager.recordUsage(userId, 1L);
        assertEquals(ComputeUnitQuotaManager.QuotaStatus.EXHAUSTED_DEGRADED, status);
        assertEquals(1000L, manager.getQuota(userId).getUsed5Hour(), "Must not accumulate negative debt");
    }

    @Test
    void testExtraPrepaidCreditsFallback() {
        ComputeUnitQuotaManager manager = ComputeUnitQuotaManager.of(1000L, 5000L);
        long userId = 105L;

        // Add 500 extra credits
        manager.addCredits(userId, 500L);

        // Exhaust 5h limit
        manager.recordUsage(userId, 1000L);

        // Next consumption should be deducted from extra credits
        ComputeUnitQuotaManager.QuotaStatus status = manager.recordUsage(userId, 200L);
        assertEquals(ComputeUnitQuotaManager.QuotaStatus.OK, status);
        assertEquals(300L, manager.getQuota(userId).getExtraCredits());
    }
}
