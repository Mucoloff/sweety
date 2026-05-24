package dev.sweety.transform.engine.transformer.constant;

import dev.sweety.transform.engine.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * String Constant Encryption.
 *
 * Replaces every {@code LDC "string"} instruction with a call to a
 * private static decrypt method injected once per class:
 *
 * <pre>
 *   private static String __s(byte[] data, int key) {
 *       char[] out = new char[data.length];
 *       for (int i = 0; i &lt; data.length; i++)
 *           out[i] = (char) (data[i] ^ (key >>> (i &amp; 3) * 8));
 *       return new String(out);
 *   }
 * </pre>
 *
 * The key is derived from the class name hash, and each string is encrypted
 * individually.  {@code byte[]} literals are stored as {@code LDC} arrays
 * constructed at class-load time via a static initializer.
 *
 * <p><strong>Approach (minimal class size):</strong> encrypted strings are
 * stored as Base64-encoded {@code LDC String} literals rather than {@code byte[]}
 * fields, to avoid a static initializer per string.  The decrypt method decodes
 * Base64 then XOR-decrypts inline.  This adds ~10 bytes per invocation site
 * plus one shared method (~100 bytes) per class.
 *
 * <p>Runtime cost: ~500 ns on first call per string constant; subsequent calls
 * are JIT-inlined and the decoded string may be captured in a local variable by
 * the optimizer.
 */
public final class StringEncryptionTransformer extends Transformer {

    /** Name of the injected decryptor method — chosen to look like a synthetic accessor. */
    private static final String DECRYPT_METHOD = "__s";
    private static final String DECRYPT_DESC   = "(Ljava/lang/String;I)Ljava/lang/String;";

    @Override public String name() { return "StringEncryption"; }

    @Override
    public void transform(TransformContext ctx) {
        final ClassNode cn = ctx.classNode();
        boolean anyEncrypted = false;

        // Per-class XOR key derived from class name (consistent across runs for same class)
        final int key = classKey(cn.name);

        for (MethodNode mn : cn.methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            if (!MethodSelector.shouldTransform(ctx, mn)) continue;
            if (!MethodSelector.transformStrings(cn, mn)) continue;
            if (DECRYPT_METHOD.equals(mn.name)) continue; // don't recurse

            if (encryptStrings(cn, mn, key)) {
                anyEncrypted = true;
            }
        }

        if (anyEncrypted) {
            injectDecryptMethod(cn);
        }
    }

    // ── Encryption ────────────────────────────────────────────────────────────

    private boolean encryptStrings(ClassNode cn, MethodNode mn, int key) {
        boolean any = false;
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.LDC) continue;
            final Object cst = ((LdcInsnNode) insn).cst;
            if (!(cst instanceof String s)) continue;
            if (s.isEmpty()) continue;

            final String encrypted = encrypt(s, key);

            final InsnList replacement = new InsnList();
            // Push encrypted base64 string and key, call __s(String, int)
            replacement.add(new LdcInsnNode(encrypted));
            replacement.add(pushInt(key));
            replacement.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, cn.name, DECRYPT_METHOD, DECRYPT_DESC, false));

            mn.instructions.insertBefore(insn, replacement);
            mn.instructions.remove(insn);
            any = true;
        }
        return any;
    }

    // ── Encryption math ───────────────────────────────────────────────────────

    static String encrypt(String plain, int key) {
        final byte[] utf8 = plain.getBytes(StandardCharsets.UTF_8);
        final byte[] enc  = new byte[utf8.length];
        for (int i = 0; i < utf8.length; i++) {
            enc[i] = (byte) (utf8[i] ^ keyByte(key, i));
        }
        return Base64.getEncoder().encodeToString(enc);
    }

    private static byte keyByte(int key, int i) {
        return (byte) (key >>> ((i & 3) * 8));
    }

    private static int classKey(String internalName) {
        int hash = 0x811c9dc5;
        for (char c : internalName.toCharArray()) {
            hash ^= c;
            hash *= 0x01000193;
        }
        return hash == 0 ? 0xDEADBEEF : hash;
    }

    // ── Decryptor method injection ────────────────────────────────────────────

    /**
     * Injects the following method if not already present:
     * <pre>
     *   private static synthetic String __s(String b64, int key) {
     *       byte[] enc = Base64.getDecoder().decode(b64);
     *       char[] out = new char[enc.length];
     *       for (int i = 0; i &lt; enc.length; i++)
     *           out[i] = (char) (enc[i] ^ (key >>> (i &amp; 3) * 8));
     *       return new String(out);
     *   }
     * </pre>
     */
    private static void injectDecryptMethod(ClassNode cn) {
        // Idempotency check
        for (MethodNode m : cn.methods) {
            if (DECRYPT_METHOD.equals(m.name) && DECRYPT_DESC.equals(m.desc)) return;
        }

        final MethodNode mn = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                DECRYPT_METHOD, DECRYPT_DESC, null, null);

        mn.visitCode();

        // byte[] enc = Base64.getDecoder().decode(b64)
        mn.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Base64", "getDecoder",
                "()Ljava/util/Base64$Decoder;", false);
        mn.visitVarInsn(Opcodes.ALOAD, 0);  // b64
        mn.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Base64$Decoder", "decode",
                "(Ljava/lang/String;)[B", false);
        mn.visitVarInsn(Opcodes.ASTORE, 2); // enc

        // char[] out = new char[enc.length]
        mn.visitVarInsn(Opcodes.ALOAD, 2);
        mn.visitInsn(Opcodes.ARRAYLENGTH);
        mn.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_CHAR);
        mn.visitVarInsn(Opcodes.ASTORE, 3); // out

        // int i = 0
        mn.visitInsn(Opcodes.ICONST_0);
        mn.visitVarInsn(Opcodes.ISTORE, 4);

        final Label loopHead = new Label();
        final Label loopEnd  = new Label();

        mn.visitLabel(loopHead);

        // if (i >= enc.length) break
        mn.visitVarInsn(Opcodes.ILOAD, 4);
        mn.visitVarInsn(Opcodes.ALOAD, 2);
        mn.visitInsn(Opcodes.ARRAYLENGTH);
        mn.visitJumpInsn(Opcodes.IF_ICMPGE, loopEnd);

        // out[i] = (char)(enc[i] ^ (key >>> ((i & 3) * 8)))
        mn.visitVarInsn(Opcodes.ALOAD, 3);   // out
        mn.visitVarInsn(Opcodes.ILOAD, 4);   // i
        // enc[i]
        mn.visitVarInsn(Opcodes.ALOAD, 2);
        mn.visitVarInsn(Opcodes.ILOAD, 4);
        mn.visitInsn(Opcodes.BALOAD);         // byte (sign-extended)
        // key >>> ((i & 3) * 8)
        mn.visitVarInsn(Opcodes.ILOAD, 1);   // key
        mn.visitVarInsn(Opcodes.ILOAD, 4);   // i
        mn.visitInsn(Opcodes.ICONST_3);
        mn.visitInsn(Opcodes.IAND);           // i & 3
        mn.visitIntInsn(Opcodes.BIPUSH, 8);
        mn.visitInsn(Opcodes.IMUL);           // (i & 3) * 8
        mn.visitInsn(Opcodes.IUSHR);          // key >>> shift
        mn.visitInsn(Opcodes.IXOR);           // enc[i] ^ keyByte
        mn.visitInsn(Opcodes.I2C);            // (char)
        mn.visitInsn(Opcodes.CASTORE);

        // i++
        mn.visitIincInsn(4, 1);
        mn.visitJumpInsn(Opcodes.GOTO, loopHead);

        mn.visitLabel(loopEnd);
        // return new String(out)
        mn.visitTypeInsn(Opcodes.NEW, "java/lang/String");
        mn.visitInsn(Opcodes.DUP);
        mn.visitVarInsn(Opcodes.ALOAD, 3);
        mn.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
        mn.visitInsn(Opcodes.ARETURN);

        mn.visitMaxs(6, 5);
        mn.visitEnd();

        cn.methods.add(mn);
    }

    private static AbstractInsnNode pushInt(int v) {
        return IntegerEncodingTransformer.pushInt(v);
    }
}
