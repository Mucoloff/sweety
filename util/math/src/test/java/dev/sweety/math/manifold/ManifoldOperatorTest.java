package dev.sweety.math.manifold;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ManifoldOperatorTest {

    public static final class Vec2 {
        public final double x;
        public final double y;

        public Vec2(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Vec2 plus(Vec2 other) {
            return new Vec2(this.x + other.x, this.y + other.y);
        }

        public Vec2 minus(Vec2 other) {
            return new Vec2(this.x - other.x, this.y - other.y);
        }

        public Vec2 times(double scalar) {
            return new Vec2(this.x * scalar, this.y * scalar);
        }
    }

    @Test
    @DisplayName("Manifold operator overloading allows arithmetic expressions in Java")
    void testOperatorOverloading() {
        Vec2 v1 = new Vec2(1.0, 2.0);
        Vec2 v2 = new Vec2(3.0, 4.0);

        Vec2 sum = v1 + v2;
        assertEquals(4.0, sum.x, 1e-6);
        assertEquals(6.0, sum.y, 1e-6);

        Vec2 diff = v2 - v1;
        assertEquals(2.0, diff.x, 1e-6);
        assertEquals(2.0, diff.y, 1e-6);

        Vec2 scaled = v1 * 3.0;
        assertEquals(3.0, scaled.x, 1e-6);
        assertEquals(6.0, scaled.y, 1e-6);
    }
}
