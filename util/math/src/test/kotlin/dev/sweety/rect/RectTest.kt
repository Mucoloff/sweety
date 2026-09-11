package dev.sweety.rect

import dev.sweety.rect.common.Rect
import dev.sweety.rect.immutable.ImmutRect
import dev.sweety.rect.mutable.MutRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RectTest {

    @Test
    fun testDimensionsAndCenter() {
        val rect = Rect.of(10f, 20f, 100f, 50f)
        assertEquals(10f, rect.x())
        assertEquals(20f, rect.y())
        assertEquals(100f, rect.width())
        assertEquals(50f, rect.height())
        assertEquals(10f, rect.minX())
        assertEquals(20f, rect.minY())
        assertEquals(110f, rect.maxX())
        assertEquals(70f, rect.maxY())
        assertEquals(60f, rect.centerX())
        assertEquals(45f, rect.centerY())
    }

    @Test
    fun testContainsPoint() {
        val rect = Rect.of(10f, 20f, 100f, 50f)
        assertTrue(rect.contains(10.0, 20.0))
        assertTrue(rect.contains(50.0, 45.0))
        assertTrue(rect.contains(109.9, 69.9))

        assertFalse(rect.contains(9.9, 20.0))
        assertFalse(rect.contains(10.0, 19.9))
        assertFalse(rect.contains(110.0, 45.0))
        assertFalse(rect.contains(50.0, 70.0))
    }

    @Test
    fun testIntersectsAndContainsRect() {
        val r1 = Rect.of(0f, 0f, 100f, 100f)
        val r2 = Rect.of(50f, 50f, 100f, 100f)
        val r3 = Rect.of(200f, 200f, 50f, 50f)
        val inside = Rect.of(10f, 10f, 20f, 20f)

        assertTrue(r1.intersects(r2))
        assertTrue(r2.intersects(r1))
        assertFalse(r1.intersects(r3))

        assertTrue(r1.contains(inside))
        assertFalse(r1.contains(r2))
    }

    @Test
    fun testSlicingForSliders() {
        val full = Rect.of(100f, 100f, 200f, 40f)
        val halfX = full.sliceX(0.5f)
        assertEquals(100f, halfX.x())
        assertEquals(100f, halfX.y())
        assertEquals(100f, halfX.width())
        assertEquals(40f, halfX.height())

        val quarterY = full.sliceY(0.25f)
        assertEquals(200f, quarterY.width())
        assertEquals(10f, quarterY.height())
    }

    @Test
    fun testLerpInterpolation() {
        val start = Rect.of(0f, 0f, 100f, 100f)
        val end = Rect.of(100f, 200f, 200f, 300f)
        val mid = start.lerp(end, 0.5f)

        assertEquals(50f, mid.x())
        assertEquals(100f, mid.y())
        assertEquals(150f, mid.width())
        assertEquals(200f, mid.height())
    }

    @Test
    fun testMutableInPlaceAndSnapshot() {
        val mut = Rect.mutableOf(10f, 10f, 50f, 50f)
        assertEquals(10f, mut.x())

        mut.set(20f, 30f, 80f, 90f)
        assertEquals(20f, mut.x())
        assertEquals(30f, mut.y())
        assertEquals(80f, mut.width())
        assertEquals(90f, mut.height())

        val snapshot: ImmutRect = mut.toImmutable()
        assertEquals(20f, snapshot.x())
        assertEquals(80f, snapshot.width())

        // Mutating the mutable rect does not affect snapshot
        mut.set(0f, 0f, 10f, 10f)
        assertEquals(20f, snapshot.x())
        assertEquals(80f, snapshot.width())
        assertNotSame(mut, snapshot)
    }
}
