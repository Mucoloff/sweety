package dev.sweety.color

import java.awt.Color

/**
 * Packed ARGB color value (bits 31-24 = alpha, 23-16 = red, 15-8 = green, 7-0 = blue).
 * Zero-allocation wrapper — erased to Int at runtime.
 *
 * Java usage:  EColor c = EColor.of(255, 200, 100); int argb = c.getArgb();
 * Kotlin usage: val c = EColor.of(255, 200, 100); val argb = c.argb
 */
@JvmInline
value class EColor(val argb: Int) {

    // ── int channel accessors (0-255) ─────────────────────────────────────────
    val a: Int get() = (argb ushr 24) and 0xFF
    val r: Int get() = (argb ushr 16) and 0xFF
    val g: Int get() = (argb ushr  8) and 0xFF
    val b: Int get() =  argb          and 0xFF

    // ── float channel accessors (0f-1f) ───────────────────────────────────────
    val af: Float get() = a / 255f
    val rf: Float get() = r / 255f
    val gf: Float get() = g / 255f
    val bf: Float get() = b / 255f

    // ── channel-replace builders ──────────────────────────────────────────────
    fun withA(v: Int)   = EColor((argb and 0x00FFFFFF)              or ((v and 0xFF) shl 24))
    fun withR(v: Int)   = EColor((argb and 0xFF00FFFF.toInt())      or ((v and 0xFF) shl 16))
    fun withG(v: Int)   = EColor((argb and 0xFFFF00FF.toInt())      or ((v and 0xFF) shl  8))
    fun withB(v: Int)   = EColor((argb and 0xFFFFFF00.toInt())      or  (v and 0xFF))

    fun withA(v: Float) = withA((v * 255f + 0.5f).toInt().coerceIn(0, 255))
    fun withR(v: Float) = withR((v * 255f + 0.5f).toInt().coerceIn(0, 255))
    fun withG(v: Float) = withG((v * 255f + 0.5f).toInt().coerceIn(0, 255))
    fun withB(v: Float) = withB((v * 255f + 0.5f).toInt().coerceIn(0, 255))

    fun withAlpha(alpha: Int)   = withA(alpha)
    fun withAlpha(alpha: Float) = withA(alpha)

    // ── color operations ──────────────────────────────────────────────────────
    fun mix(other: EColor, t: Float): EColor {
        val inv = 1f - t
        return of(
            a = (a * inv + other.a * t).toInt(),
            r = (r * inv + other.r * t).toInt(),
            g = (g * inv + other.g * t).toInt(),
            b = (b * inv + other.b * t).toInt(),
        )
    }

    fun mix(other: Int, t: Float) = mix(EColor(other), t)

    fun darker(factor: Float = 0.7f) = of(a,
        (r * factor).toInt().coerceAtLeast(0),
        (g * factor).toInt().coerceAtLeast(0),
        (b * factor).toInt().coerceAtLeast(0),
    )

    fun lighter(factor: Float = 0.7f) = of(a,
        (r / factor).toInt().coerceAtMost(255),
        (g / factor).toInt().coerceAtMost(255),
        (b / factor).toInt().coerceAtMost(255),
    )

    // ── conversions ───────────────────────────────────────────────────────────
    /** RGBA float array [r, g, b, a] in [0,1]. Primitive — no boxing. */
    fun floats(): FloatArray = floatArrayOf(rf, gf, bf, af)

    fun floats(comp: FloatArray) {
        comp[0] = rf
        comp[1] = gf
        comp[2] = bf
        comp[3] = af
    }

    fun values(): IntArray = intArrayOf(r, g, b, a)

    fun values(comp: IntArray) {
        comp[0] = r
        comp[1] = g
        comp[2] = b
        comp[3] = a
    }

    fun toAwtColor(): Color = Color(r, g, b, a)

    override fun toString() = "#%02x%02x%02x%02x".format(r, g, b, a)

    // ── companion ─────────────────────────────────────────────────────────────
    companion object {

        // Factories — all return EColor

        @JvmStatic fun of(a: Int, r: Int, g: Int, b: Int) = EColor(
            ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        )

        @JvmStatic fun of(r: Int, g: Int, b: Int) = of(255, r, g, b)

        @JvmStatic fun ofRgb(rgb: Int)   = EColor(rgb or (0xFF shl 24))
        @JvmStatic fun ofArgb(argb: Int) = EColor(argb)

        @JvmStatic fun fromAwtColor(c: Color) = of(c.alpha, c.red, c.green, c.blue)

        // Java-friendly int factories (return int, no name mangling).
        // Kotlin callers can use of() directly; Java callers use argbOf().

        @JvmStatic fun rgbOf(r: Int, g: Int, b: Int,a: Int): Int = of(a, r, g, b).argb
        @JvmStatic fun rgbOf(r: Int, g: Int, b: Int): Int = rgbOf(r, g, b, 255)

        @JvmStatic fun argbOf(a: Int, r: Int, g: Int, b: Int): Int = of(a, r, g, b).argb
        @JvmStatic fun argbOf(r: Int, g: Int, b: Int): Int = argbOf(255, r, g, b)

        // Static int→int utilities (for callers using raw ARGB ints)

        @JvmStatic fun floats(color: Int) = EColor(color).floats()
        @JvmStatic fun floats(color: Int, comp: FloatArray) = EColor(color).floats(comp)

        @JvmStatic
        fun values(color: Int) = EColor(color).values()
        @JvmStatic
        fun values(color: Int, comp: IntArray) = EColor(color).values(comp)

        @JvmStatic fun mix(c1: Int, c2: Int, t: Float)         = EColor(c1).mix(EColor(c2), t).argb
        @JvmStatic fun withAlpha(c: Int, alpha: Int)            = EColor(c).withA(alpha).argb
        @JvmStatic fun withAlpha(c: Int, alpha: Float)          = EColor(c).withA(alpha).argb
        @JvmStatic fun darker(c: Int, factor: Float = 0.7f)     = EColor(c).darker(factor).argb
        @JvmStatic fun lighter(c: Int, factor: Float = 0.7f)    = EColor(c).lighter(factor).argb
    }
}
