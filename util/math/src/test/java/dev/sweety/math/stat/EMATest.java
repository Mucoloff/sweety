package dev.sweety.math.stat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EMATest {

    @Test
    public void testInitialization() {
        EMA ema = new EMA(0.5);
        assertFalse(ema.isInitialized());
        assertEquals(0.0, ema.get());

        double first = ema.update(100.0);
        assertTrue(ema.isInitialized());
        assertEquals(100.0, first);
        assertEquals(100.0, ema.get());
    }

    @Test
    public void testSmoothingConvergence() {
        EMA ema = new EMA(0.5);
        ema.update(100.0); // 100.0
        ema.update(50.0);  // 0.5 * 50 + 0.5 * 100 = 75.0
        assertEquals(75.0, ema.get(), 1e-6);

        ema.update(50.0);  // 0.5 * 50 + 0.5 * 75 = 62.5
        assertEquals(62.5, ema.get(), 1e-6);
    }

    @Test
    public void testReset() {
        EMA ema = new EMA(0.25);
        ema.update(42.0);
        assertTrue(ema.isInitialized());

        ema.reset();
        assertFalse(ema.isInitialized());
        assertEquals(0.0, ema.get());

        ema.update(88.0);
        assertEquals(88.0, ema.get());
    }

    @Test
    public void testInvalidAlpha() {
        assertThrows(IllegalArgumentException.class, () -> new EMA(0.0));
        assertThrows(IllegalArgumentException.class, () -> new EMA(-0.5));
        assertThrows(IllegalArgumentException.class, () -> new EMA(1.1));
    }
}
