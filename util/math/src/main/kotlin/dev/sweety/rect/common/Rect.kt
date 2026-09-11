package dev.sweety.rect.common

import dev.sweety.rect.immutable.ImmutRect
import dev.sweety.rect.mutable.MutRect

/**
 * Common 2D rectangle interface.
 * Models floating-point bounds with hit-testing, slicing, and interpolation support.
 */
interface Rect {
    fun x(): Float
    fun y(): Float
    fun width(): Float
    fun height(): Float

    fun minX(): Float = x()
    fun minY(): Float = y()
    fun maxX(): Float = x() + width()
    fun maxY(): Float = y() + height()

    fun centerX(): Float = x() + width() * 0.5f
    fun centerY(): Float = y() + height() * 0.5f

    fun contains(px: Double, py: Double): Boolean {
        return px >= minX() && px < maxX() && py >= minY() && py < maxY()
    }

    fun contains(px: Float, py: Float): Boolean {
        return px >= minX() && px < maxX() && py >= minY() && py < maxY()
    }

    fun contains(other: Rect): Boolean {
        return other.minX() >= minX() && other.maxX() <= maxX() &&
                other.minY() >= minY() && other.maxY() <= maxY()
    }

    fun intersects(other: Rect): Boolean {
        return minX() < other.maxX() && maxX() > other.minX() &&
                minY() < other.maxY() && maxY() > other.minY()
    }

    fun lerp(target: Rect, t: Float): Rect {
        val clampedT = t.coerceIn(0f, 1f)
        return of(
            x() + (target.x() - x()) * clampedT,
            y() + (target.y() - y()) * clampedT,
            width() + (target.width() - width()) * clampedT,
            height() + (target.height() - height()) * clampedT
        )
    }

    fun sliceX(progress: Float): Rect {
        val p = progress.coerceIn(0f, 1f)
        return withSize(width() * p, height())
    }

    fun sliceY(progress: Float): Rect {
        val p = progress.coerceIn(0f, 1f)
        return withSize(width(), height() * p)
    }

    fun withPos(x: Float, y: Float): Rect = of(x, y, width(), height())

    fun withSize(width: Float, height: Float): Rect = of(x(), y(), width, height)

    fun inflate(amount: Float): Rect = of(
        x() - amount,
        y() - amount,
        width() + amount * 2f,
        height() + amount * 2f
    )

    fun inflate(dx: Float, dy: Float): Rect = of(
        x() - dx,
        y() - dy,
        width() + dx * 2f,
        height() + dy * 2f
    )

    fun toImmutable(): ImmutRect = ImmutRect(x(), y(), width(), height())

    fun toMutable(): MutRect = MutRect(x(), y(), width(), height())

    companion object {
        @JvmStatic
        fun of(x: Float = 0f, y: Float = 0f, width: Float = 0f, height: Float = 0f): ImmutRect {
            return ImmutRect(x, y, width, height)
        }

        @JvmStatic
        fun mutableOf(x: Float = 0f, y: Float = 0f, width: Float = 0f, height: Float = 0f): MutRect {
            return MutRect(x, y, width, height)
        }
    }
}
