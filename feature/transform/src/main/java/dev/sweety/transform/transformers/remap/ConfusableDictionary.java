package dev.sweety.transform.transformers.remap;

public enum ConfusableDictionary {
    /** Alternations of uppercase 'I' and lowercase 'l' (e.g., "IllIIlIl", "llIlIllI") */
    ILL(new String[]{"I", "l"}),

    /** Alternations of uppercase 'O' and digit '0' (e.g., "OO0O0OO0", "O00OO00O") */
    OH_ZERO(new String[]{"O", "0"}),

    /** Confusable look-alike digraph 'rn' and letter 'm' (e.g., "rnmrnrn", "mrnrmrn") */
    RN_M(new String[]{"rn", "m"}),

    /** Alternations of 'p', 'q', 'd', 'b' */
    PQ_DB(new String[]{"p", "q", "d", "b"}),

    /** Alternations of 'n', 'h' */
    NH(new String[]{"n", "h"});

    private final String[] tokens;

    ConfusableDictionary(String[] tokens) {
        this.tokens = tokens;
    }

    public String[] getTokens() {
        return tokens;
    }
}
