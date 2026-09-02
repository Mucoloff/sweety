package dev.sweety.transform.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    private ClassLoader frameClassLoader;

    public void setFrameClassLoader(ClassLoader cl) {
        this.frameClassLoader = cl;
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
        if (frameClassLoader != null) {
            ctx.setFrameClassLoader(frameClassLoader);
        }

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

            // Delivery runs on the server, which has neither Minecraft nor the client classes on its
            // classpath. COMPUTE_FRAMES would otherwise classload every referenced type to find common
            // supers and throw. Fall back to Object for any unresolvable pair — Object is the safe
            // common super and yields verifiable frames for our transformed (gate-shaped) methods.
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };

        try {
            node.accept(writer);
        } catch (Throwable e) {
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
                    .add(new dev.sweety.transform.transformers.virtualize.VirtualizerTransformer())
                    .add(new dev.sweety.transform.transformers.control.GotoNormalizationTransformer())
                    .add(new dev.sweety.transform.transformers.control.ConditionalMutationTransformer())
                    .add(new dev.sweety.transform.transformers.control.ExceptionFlowTransformer())
                    .add(new dev.sweety.transform.transformers.constant.StringEncryptionTransformer())
                    .add(new dev.sweety.transform.transformers.constant.IntegerEncodingTransformer())
                    .build();
        }
    }
}
