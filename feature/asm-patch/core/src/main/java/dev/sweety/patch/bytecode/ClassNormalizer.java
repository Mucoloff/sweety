package dev.sweety.patch.bytecode;

public interface ClassNormalizer {
    byte[] normalize(byte[] classBytes);
}