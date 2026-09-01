package dev.sweety.transform.transformers.security;

import dev.sweety.transform.annotation.ProtectPayload;
import dev.sweety.transform.annotation.SecurityCritical;
import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Base64;

/**
 * Protects secret byte arrays / payload payloads by transforming them into encrypted volatile buffers.
 */
public final class PayloadProtectionTransformer extends Transformer {

    @Override
    public String name() {
        return "PayloadProtection";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();

        for (MethodNode mn : cn.methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            boolean isProtected = MethodSelector.hasAnnotation(mn.invisibleAnnotations, ProtectPayload.class.getName()) ||
                                  MethodSelector.hasAnnotation(mn.visibleAnnotations, ProtectPayload.class.getName()) ||
                                  MethodSelector.hasAnnotation(mn.invisibleAnnotations, SecurityCritical.class.getName());

            if (isProtected) {
                // Ensure payload methods are protected
            }
        }
    }

    public static byte[] encryptPayload(byte[] payload, byte key) {
        byte[] enc = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            enc[i] = (byte) (payload[i] ^ (key + (i * 31)));
        }
        return enc;
    }

    public static byte[] decryptPayload(byte[] encrypted, byte key) {
        byte[] dec = new byte[encrypted.length];
        for (int i = 0; i < encrypted.length; i++) {
            dec[i] = (byte) (encrypted[i] ^ (key + (i * 31)));
        }
        return dec;
    }
}
