package dev.sweety.core.math;

import lombok.experimental.UtilityClass;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class RandomUtils {

    public final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
    private final SecureRandom SECURE_RANDOM = new SecureRandom();

    // --- Base ---

    public <E> E randomElement(Collection<? extends E> collection) {
        if (collection == null || collection.isEmpty()) return null;
        int index = RANDOM.nextInt(collection.size());
        if (collection instanceof List)
            return ((List<? extends E>) collection).get(index);
        Iterator<? extends E> iter = collection.iterator();
        for (int i = 0; i < index; i++) iter.next();
        return iter.next();
    }

    public int range(int min, int max) {
        return RANDOM.nextInt(min, max + 1);
    }

    // --- Secure Random ---

    public byte[] secureBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public String secureToken(int length) {
        byte[] bytes = secureBytes(length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public UUID secureUUID() {
        byte[] r = secureBytes(16);
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (r[i] & 0xff);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (r[i] & 0xff);
        msb &= ~(0xfL << 12);
        msb |= (4L << 12);
        lsb &= ~(0x3L << 62);
        lsb |= (0x2L << 62);
        return new UUID(msb, lsb);
    }

    public SecretKey generateAESKey(int bits) {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(bits, SECURE_RANDOM);
            return kg.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }

    public KeyPair generateRSAKeyPair(int bits) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(bits, SECURE_RANDOM);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    // --- Utils ---

    public int secureInt(int bound) {
        return SECURE_RANDOM.nextInt(bound);
    }

    public long secureLong() {
        return SECURE_RANDOM.nextLong();
    }

    public boolean secureBoolean() {
        return SECURE_RANDOM.nextBoolean();
    }
}
