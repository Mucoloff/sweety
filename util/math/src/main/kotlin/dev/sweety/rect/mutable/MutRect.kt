package dev.sweety.rect.mutable

import dev.sweety.rect.common.Rect
import dev.sweety.rect.immutable.ImmutRect

/**
 * Mutable 2D rectangle.
 * Designed for high-performance animations, sliders, and in-place layout morphing with zero heap allocations.
 */
class MutRect(
    private var x: Float = 0f,
    private var y: Float = 0f,
    private var width: Float = 0f,
    private var height: Float = 0f
) : Rect {

    override fun x(): Float = x
    override fun y(): Float = y
    override fun width(): Float = width
    override fun height(): Float = height

    fun setX(x: Float) {
        this.x = x
    }

    fun setY(y: Float) {
        this.y = y
    }

    fun setWidth(width: Float) {
        this.width = width
    }

    fun setHeight(height: Float) {
        this.height = height
    }

    fun set(x: Float, y: Float, width: Float, height: Float) {
        this.x = x
        this.y = y
        this.width = width
        this.height = height
    }

    fun set(other: Rect) {
        set(other.x(), other.y(), other.width(), other.height())
    }

    fun selfLerp(target: Rect, t: Float) {
        val clampedT = t.coerceIn(0f, 1f)
        this.x += (target.x() - this.x) * clampedT
        this.y += (target.y() - this.y) * clampedT
        this.width += (target.width() - this.width) * clampedT
        this.height += (target.height() - this.height) * clampedT
    }

    fun selfInflate(amount: Float) {
        this.x -= amount
        this.y -= amount
        this.width += amount * 2f
        this.height += amount * 2f
    }

    fun selfInflate(dx: Float, dy: Float) {
        this.x -= dx
        this.y -= dy
        this.width += dx * 2f
        this.height += dy * 2f
    }

    fun selfOffset(dx: Float, dy: Float) {
        this.x += dx
        this.y += dy
    }

    override fun toImmutable(): ImmutRect = ImmutRect(x, y, width, height)

    override fun toMutable(): MutRect = MutRect(x, y, width, height)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rect) return false
        return x == other.x() && y == other.y() && width == other.width() && height == other.height()
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        return result
    }

    override fun toString(): String = "MutRect(x=$x, y=$y, width=$width, height=$height)"
}
