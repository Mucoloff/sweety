package dev.sweety.transform.transformers.virtualize;

import dev.sweety.util.signature.SessionCrypto;
import dev.sweety.transform.engine.MethodSelector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * Server-side, on-demand counterpart to {@link VirtualizerTransformer}: recompiles a class's
 * {@code @Virtualize} methods to VM bytecode and encrypts each payload with a key that only exists
 * after the client's first live epoch rotation ({@code SessionCrypto.deriveEpochKey}), instead of
 * baking plaintext bytecode into the jar at initial delivery time.
 *
 * <p>Used by the {@code RequestVirtualBytecodeTransaction} handler (server/session) — the initial jar
 * delivery (still via {@link VirtualizerTransformer}/{@link dev.sweety.transform.engine.TransformPipeline})
 * leaves each method's storage field unset until this exchange happens; this service produces the
 * real, encrypted contents for exactly those fields, keyed by
 * {@link VirtualizerTransformer#fieldNameFor(MethodNode)} so both sides agree on placement.
 */
public final class VirtualBytecodeService {

    private VirtualBytecodeService() {}

    /**
     * @param originalClassBytes the class's original (untransformed) bytecode, as read from the base jar
     * @param epochKey           32-byte AES key from {@code SessionCrypto.deriveEpochKey(jwt, counter, nonce)}
     * @return field name → AES-256-GCM ciphertext, one entry per {@code @Virtualize} method found;
     *         empty if the class carries none
     */
    public static Map<String, byte[]> compileAndEncrypt(byte[] originalClassBytes, byte[] epochKey) {
        final ClassNode cn = new ClassNode();
        new ClassReader(originalClassBytes).accept(cn, ClassReader.SKIP_FRAMES);

        final Map<String, byte[]> out = new LinkedHashMap<>();
        for (MethodNode mn : cn.methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            if (!MethodSelector.shouldVirtualize(mn)) continue;
            if (!MethodSelector.isVirtualizable(mn)) continue;

            byte[] vmCode = VMCompiler.compile(cn.name, mn);
            out.put(VirtualizerTransformer.fieldNameFor(mn), SessionCrypto.encrypt(vmCode, epochKey));
        }
        return out;
    }

    /**
     * Jar-wide counterpart to {@link #compileAndEncrypt(byte[], byte[])}: scans every {@code .class}
     * entry in the base runtime jar, keeping only classes that actually carry {@code @Virtualize}
     * methods.
     *
     * @return owner internal class name → (field name → ciphertext), empty entries omitted
     */
    public static Map<String, Map<String, byte[]>> compileAndEncryptJar(byte[] jarBytes, byte[] epochKey) throws Exception {
        final Map<String, Map<String, byte[]>> byOwner = new LinkedHashMap<>();
        try (InputStream raw = new ByteArrayInputStream(jarBytes); JarInputStream jar = new JarInputStream(raw)) {
            ZipEntry entry;
            while ((entry = jar.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) {
                    continue;
                }
                byte[] classBytes = jar.readAllBytes();
                Map<String, byte[]> fields = compileAndEncrypt(classBytes, epochKey);
                if (!fields.isEmpty()) {
                    byOwner.put(new ClassReader(classBytes).getClassName(), fields);
                }
            }
        }
        return byOwner;
    }
}
