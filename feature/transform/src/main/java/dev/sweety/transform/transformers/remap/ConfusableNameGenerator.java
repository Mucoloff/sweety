package dev.sweety.transform.transformers.remap;

import java.util.Random;

public final class ConfusableNameGenerator {

    private ConfusableNameGenerator() {}

    public static String generate(int index, ConfusableDictionary dictionary, int length) {
        String[] tokens = dictionary.getTokens();
        StringBuilder sb = new StringBuilder();

        // Use deterministic pseudo-random sequence based on index and dictionary hash
        long seed = (long) index * 31337L + dictionary.ordinal() * 10007L + 0xCAFEBABE;
        Random rng = new Random(seed);

        // Ensure identifier starts with a valid Java letter (if digit '0' is chosen first, fallback to 'O')
        String first = tokens[rng.nextInt(tokens.length)];
        if ("0".equals(first)) {
            first = "O";
        }
        sb.append(first);

        while (sb.length() < length) {
            sb.append(tokens[rng.nextInt(tokens.length)]);
        }

        return sb.toString();
    }
}
