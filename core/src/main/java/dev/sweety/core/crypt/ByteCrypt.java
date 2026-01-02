package dev.sweety.core.crypt;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ByteCrypt {

    private static final int PRIME = 0x9E3779B9; // costante tipo golden ratio (usata in TEA)

    public byte[] encrypt(byte[] input, byte[] signature) {
        return transform(xor(input, signature), signature, true);
    }

    public byte[] decrypt(byte[] input, byte[] signature) {
        return transform(xor(input, signature), signature, false);
    }

    private byte[] transform(byte[] input, byte[] signature, boolean encrypt) {
        int seed = deriveSeed(signature);
        byte[] output = new byte[input.length];
        int shift = (seed & 0xFF);

        for (int i = 0; i < input.length; i++) {
            seed = next(seed);
            int key = (seed >>> 8) & 0xFF;
            int b = Byte.toUnsignedInt(input[i]);

            // cifra o decifra
            int v = encrypt
                    ? (b ^ key) + shift
                    : (b - shift) ^ key;

            output[i] = (byte) v;
            shift = (shift + 7) & 0xFF; // variazione costante del shift
        }

        return output;
    }

    // genera il seed dalla firma in modo deterministico
    private int deriveSeed(byte[] signature) {
        int seed = 0xC0FFEE; // base seed
        for (byte b : signature) {
            seed ^= (b * PRIME);
            seed = Integer.rotateLeft(seed, 5);
        }
        return seed;
    }

    // pseudo random step (molto rapido)
    private int next(int x) {
        x ^= (x << 13);
        x ^= (x >>> 17);
        x ^= (x << 5);
        return x * PRIME;
    }

    private byte[] xor(byte[] input, byte[] signature) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) output[i] = (byte) (input[i] ^ signature[i % signature.length]);
        return output;
    }

}
