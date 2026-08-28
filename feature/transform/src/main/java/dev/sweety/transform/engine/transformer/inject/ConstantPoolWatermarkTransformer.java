package dev.sweety.transform.engine.transformer.inject;

import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.Objects;

/**
 * Steganographic bytecode watermarking transformer.
 *
 * <p>Injects an invisible, synthetic synthetic field with the encrypted user tracking signature
 * directly into the ASM constant pool of target classes.
 * Ensures the build identity can be traced even if META-INF/MANIFEST.MF is stripped or altered.
 */
public final class ConstantPoolWatermarkTransformer extends Transformer {

    private final String watermarkSignature;

    public ConstantPoolWatermarkTransformer(String watermarkSignature) {
        this.watermarkSignature = Objects.requireNonNull(watermarkSignature, "watermarkSignature must not be null");
    }

    public static ConstantPoolWatermarkTransformer of(String watermarkSignature) {
        return new ConstantPoolWatermarkTransformer(watermarkSignature);
    }

    @Override
    public String name() {
        return "ConstantPoolWatermark";
    }

    @Override
    public void transform(TransformContext ctx) {
        final ClassNode cn = ctx.classNode();
        // Skip interfaces and module-info
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0 || "module-info".equals(cn.name)) {
            return;
        }

        // Check if watermark field already exists
        for (FieldNode fn : cn.fields) {
            if ("$aurora$wm".equals(fn.name)) {
                return;
            }
        }

        // Add synthetic private static final field carrying the watermark constant in constant pool
        final FieldNode wmField = new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                "$aurora$wm",
                "Ljava/lang/String;",
                null,
                watermarkSignature
        );

        cn.fields.add(wmField);
    }
}
