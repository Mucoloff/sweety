package dev.sweety.rect.immutable

import dev.sweety.rect.common.Rect
import dev.sweety.rect.mutable.MutRect

/**
 * Immutable, thread-safe 2D rectangle.
 * Suitable for layout snapshots and fixed bounding boxes.
 */
data class ImmutRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
) : Rect {

    override fun x(): Float = x
    override fun y(): Float = y
    override fun width(): Float = width
    override fun height(): Float = height

    override fun withPos(x: Float, y: Float): ImmutRect = copy(x = x, y = y)

    override fun withSize(width: Float, height: Float): ImmutRect = copy(width = width, height = height)

    override fun lerp(target: Rect, t: Float): ImmutRect {
        val clampedT = t.coerceIn(0f, 1f)
        return copy(
            x = x + (target.x() - x) * clampedT,
            y = y + (target.y() - y) * clampedT,
            width = width + (target.width() - width) * clampedT,
            height = height + (target.height() - height) * clampedT
        )
    }

    override fun sliceX(progress: Float): ImmutRect {
        val p = progress.coerceIn(0f, 1f)
        return copy(width = width * p)
    }

    override fun sliceY(progress: Float): ImmutRect {
        val p = progress.coerceIn(0f, 1f)
        return copy(height = height * p)
    }

    override fun inflate(amount: Float): ImmutRect = copy(
        x = x - amount,
        y = y - amount,
        width = width + amount * 2f,
        height = height + amount * 2f
    )

    override fun inflate(dx: Float, dy: Float): ImmutRect = copy(
        x = x - dx,
        y = y - dy,
        width = width + dx * 2f,
        height = height + dy * 2f
    )

    override fun toImmutable(): ImmutRect = this

    override fun toMutable(): MutRect = MutRect(x, y, width, height)

    companion object {
        @JvmStatic
        fun zero(): ImmutRect = ImmutRect()
    }
}
