package dev.sweety.math.splitstate.common;

import org.jetbrains.annotations.Nullable;

/**
 * Tri-state boolean: TRUE / FALSE / UNKNOWN. UNKNOWN covers two distinct origins that share
 * the same three values — a value not yet resolved (e.g. pending server confirmation) and
 * two independent checks of the same condition disagreeing — callers only ever need to
 * branch on "is it decided", so both are represented by one state, not two types.
 */
public enum Tristate {
    FALSE(Boolean.FALSE),
    TRUE(Boolean.TRUE),
    UNKNOWN(null);

    private final @Nullable Boolean value;

    Tristate(@Nullable Boolean value) {
        this.value = value;
    }

    public boolean notFalse() {
        return this != FALSE;
    }

    public boolean notTrue() {
        return this != TRUE;
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /** Kleene logical NOT: TRUE/FALSE swap, UNKNOWN stays UNKNOWN. */
    public Tristate negate() {
        return switch (this) {
            case TRUE -> FALSE;
            case FALSE -> TRUE;
            case UNKNOWN -> UNKNOWN;
        };
    }

    /** Kleene logical OR: TRUE short-circuits either side, UNKNOWN only survives if neither side is TRUE. */
    public static Tristate or(boolean first, boolean second) {
        return (first || second) ? TRUE : FALSE;
    }

    public static Tristate or(@Nullable Boolean first, @Nullable Boolean second) {
        if (Boolean.TRUE.equals(first) || Boolean.TRUE.equals(second)) return TRUE;
        if (first == null || second == null) return UNKNOWN;
        return FALSE;
    }

    public static Tristate or(Tristate first, Tristate second) {
        return or(first.value, second.value);
    }

    /** Kleene logical AND: FALSE short-circuits either side, UNKNOWN only survives if neither side is FALSE. */
    public static Tristate and(boolean first, boolean second) {
        return (first && second) ? TRUE : FALSE;
    }

    public static Tristate and(@Nullable Boolean first, @Nullable Boolean second) {
        if (Boolean.FALSE.equals(first) || Boolean.FALSE.equals(second)) return FALSE;
        if (first == null || second == null) return UNKNOWN;
        return TRUE;
    }

    public static Tristate and(Tristate first, Tristate second) {
        return and(first.value, second.value);
    }

    /** Consensus of two checks of the same condition: agreement holds, disagreement/null is unknown. */
    public static Tristate agree(boolean first, boolean second) {
        if (first ^ second) return Tristate.UNKNOWN;
        return first ? Tristate.TRUE : Tristate.FALSE;
    }

    public static Tristate agree(@Nullable Boolean first, @Nullable Boolean second) {
        if (first == null || second == null) return Tristate.UNKNOWN;
        if (first ^ second) return Tristate.UNKNOWN;
        return first ? Tristate.TRUE : Tristate.FALSE;
    }

    public static Tristate agree(Tristate first, Tristate second) {
        return agree(first.value, second.value);
    }

    public static Tristate of(@Nullable Boolean value) {
        return value == null ? Tristate.UNKNOWN : value ? Tristate.TRUE : Tristate.FALSE;
    }

    public @Nullable Boolean asBoolean() {
        return value;
    }
}
