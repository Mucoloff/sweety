package dev.sweety.data.optional

import dev.sweety.math.mask.Mask

class OptionalBoolean private constructor(private val mask: Byte) {

    val isPresent: Boolean get() = Mask.isPresent(mask, PRESENT)
    val isEmpty: Boolean get() = !isPresent

    fun get(): Boolean {
        check(isPresent) { "OptionalBoolean.empty" }
        return Mask.isPresent(mask, VALUE)
    }

    fun orElse(other: Boolean): Boolean = if (isPresent) Mask.isPresent(mask, VALUE) else other

    companion object {
        private val PRESENT = Mask.INDEXES[0]
        private val VALUE = Mask.INDEXES[1]

        @JvmField
        val EMPTY = OptionalBoolean(0)
        @JvmField
        val TRUE = OptionalBoolean(3)
        @JvmField
        val FALSE = OptionalBoolean(1)

        @JvmStatic
        fun of(value: Boolean): OptionalBoolean = if (value) TRUE else FALSE
    }
}