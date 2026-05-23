package dev.sweety.patch.verify;

import dev.sweety.patch.hash.HashFunction;

public final class Validators {

    private Validators() {
    }

    public static PatchValidator forHash(HashFunction hashFunction) {
        return new PatchValidator(hashFunction);
    }
}
