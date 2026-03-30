package dev.sweety.math.splitstate;

public enum SplitStateBoolean {
    FALSE,
    TRUE,
    POSSIBLE;

    public boolean possible() {
        return this != FALSE;
    }

    public boolean notPossible() {
        return this != TRUE;
    }

    public static SplitStateBoolean result(boolean first, boolean second) {
        if (first ^ second) return SplitStateBoolean.POSSIBLE;
        return first ? SplitStateBoolean.TRUE : SplitStateBoolean.FALSE;
    }

    public static SplitStateBoolean result(SplitStateBoolean first, SplitStateBoolean second) {
        if (second == SplitStateBoolean.POSSIBLE || first != second)
            return SplitStateBoolean.POSSIBLE;
        return first;
    }

}