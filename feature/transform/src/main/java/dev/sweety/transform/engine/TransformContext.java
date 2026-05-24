package dev.sweety.transform.engine;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

/**
 * Carries a single class through the transformer pipeline.
 * Transformers read/write the {@link ClassNode} and may attach metadata
 * via {@link #setMeta}/{@link #getMeta} for cross-transformer communication.
 */
public final class TransformContext {

    private final ClassNode classNode;
    private final String    sourceName;   // e.g. "ac/ecstacy/build/BuildReader.class"
    private final Map<String, Object> meta = new HashMap<>();

    /** Methods that have already been transformed — prevents double-processing. */
    private final Set<String> processedMethods = new HashSet<>();

    /**
     * Internal names of classes (or interfaces) whose @Transform annotation
     * should be inherited by subclasses.  Populated by TransformPipeline from
     * the first-pass scan done by TransformCLI.
     */
    private Set<String> superTransformClasses = Set.of();

    public TransformContext(ClassNode classNode, String sourceName) {
        this.classNode  = classNode;
        this.sourceName = sourceName;
    }

    /** Called by the pipeline (or qProtect adapter) to propagate class-level @Transform from supertypes. */
    public void setSuperTransformClasses(Set<String> set) { this.superTransformClasses = set; }

    /**
     * Returns true if this class directly or via superclass/interface has @Transform.
     * Only checks one level up (direct superclass); interface inheritance is not supported.
     */
    public boolean hasSuperTransform() {
        if (classNode.superName != null && superTransformClasses.contains(classNode.superName)) return true;
        if (classNode.interfaces != null) {
            for (String iface : classNode.interfaces) {
                if (superTransformClasses.contains(iface)) return true;
            }
        }
        return false;
    }

    public ClassNode classNode()  { return classNode; }
    public String    sourceName() { return sourceName; }

    public void   setMeta(String key, Object value) { meta.put(key, value); }
    @SuppressWarnings("unchecked")
    public <T> T  getMeta(String key)               { return (T) meta.get(key); }

    /** Returns true and marks as processed; false if already done (guards re-entry). */
    public boolean markProcessed(MethodNode mn) {
        return processedMethods.add(mn.name + mn.desc);
    }

    public boolean isProcessed(MethodNode mn) {
        return processedMethods.contains(mn.name + mn.desc);
    }
}
