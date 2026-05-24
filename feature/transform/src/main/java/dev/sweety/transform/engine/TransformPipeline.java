package dev.sweety.transform.engine;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;
import java.util.logging.Logger;

/**
 * Orchestrates a deterministic, ordered sequence of {@link Transformer} passes
 * over a set of class files.
 *
 * <pre>
 *   TransformPipeline pipeline = TransformPipeline.builder()
 *       .add(new GotoNormalizationTransformer())
 *       .add(new ConditionalMutationTransformer())
 *       .add(new StringEncryptionTransformer())
 *       .add(new VirtualizerTransformer())
 *       .build();
 *
 *   byte[] transformed = pipeline.transform(originalBytes, "ac/ecstacy/build/BuildReader.class");
 * </pre>
 */
public final class TransformPipeline {

    private static final Logger LOG = Logger.getLogger("EcstacyTransform");

    private final List<Transformer> transformers;

    /**
     * Internal names of classes/interfaces annotated with {@code @Transform} —
     * used to propagate transformation to their direct subclasses.
     * Populated by {@link #setSuperTransformClasses(Set)} before the transform pass.
     */
    private Set<String> superTransformClasses = Set.of();

    private TransformPipeline(List<Transformer> transformers) {
        this.transformers = List.copyOf(transformers);
    }

    /**
     * Registers the set of class internal names that carry {@code @Transform} so
     * their subclasses are also transformed.  Call once before the transform pass.
     */
    public void setSuperTransformClasses(Set<String> classes) {
        this.superTransformClasses = Set.copyOf(classes);
    }

    /**
     * Transform a single class.
     *
     * @param classBytes raw .class file bytes
     * @param sourceName path relative to classes root (e.g. "ac/ecstacy/build/BuildReader.class")
     * @return transformed .class bytes
     */
    public byte[] transform(byte[] classBytes, String sourceName) {
        // Parse
        final ClassReader reader = new ClassReader(classBytes);
        final ClassNode   node   = new ClassNode(Opcodes.ASM9);
        reader.accept(node, ClassReader.EXPAND_FRAMES);

        final TransformContext ctx = new TransformContext(node, sourceName);
        ctx.setSuperTransformClasses(superTransformClasses);

        // Apply each transformer in order
        for (Transformer t : transformers) {
            try {
                t.transform(ctx);
            } catch (Exception e) {
                LOG.warning("[" + t.name() + "] failed on " + sourceName + ": " + e.getMessage());
                // Non-fatal — continue pipeline with unmodified class for this transformer
            }
        }

        // Strip processed annotations from class-level
        MethodSelector.stripClassAnnotations(node);

        // Write back — COMPUTE_FRAMES ensures valid frames after any instruction changes
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected ClassLoader getClassLoader() {
                return TransformPipeline.class.getClassLoader();
            }
        };

        try {
            node.accept(writer);
        } catch (Exception e) {
            LOG.severe("[Pipeline] ClassWriter failed for " + sourceName + ": " + e.getMessage()
                    + " — returning original bytes");
            return classBytes; // Safety fallback: never break a class
        }

        return writer.toByteArray();
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<Transformer> list = new ArrayList<>();

        public Builder add(Transformer t) { list.add(t); return this; }

        public TransformPipeline build() { return new TransformPipeline(list); }

        /** Convenience: default pipeline used for plugin obfuscation. */
        public static TransformPipeline defaultPipeline() {
            return TransformPipeline.builder()
                    .add(new dev.sweety.transform.engine.transformer.virtualize.VirtualizerTransformer())
                    .add(new dev.sweety.transform.engine.transformer.control.GotoNormalizationTransformer())
                    .add(new dev.sweety.transform.engine.transformer.control.ConditionalMutationTransformer())
                    .add(new dev.sweety.transform.engine.transformer.control.ExceptionFlowTransformer())
                    .add(new dev.sweety.transform.engine.transformer.constant.StringEncryptionTransformer())
                    .add(new dev.sweety.transform.engine.transformer.constant.IntegerEncodingTransformer())
                    .build();
        }
    }
}
