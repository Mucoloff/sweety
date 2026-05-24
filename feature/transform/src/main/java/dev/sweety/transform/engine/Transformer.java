package dev.sweety.transform.engine;

/**
 * Base abstraction for a single transformation pass.
 *
 * Invariants:
 * <ul>
 *   <li>Stateless — no mutable instance fields; all state lives in {@link TransformContext}.</li>
 *   <li>Idempotent per method — check {@link TransformContext#markProcessed} before touching a method.</li>
 *   <li>Must not generate duplicate method names (unique suffix helpers provided by {@link TransformUtils}).</li>
 *   <li>Must produce JVM-verifiable bytecode; always pass {@code COMPUTE_FRAMES} to {@code ClassWriter}.</li>
 * </ul>
 *
 * Lifecycle: {@code transform} is called exactly once per class per pipeline run.
 * Transformers that only act on specific methods should inspect
 * {@link MethodSelector#shouldTransform}/{@link MethodSelector#shouldVirtualize}.
 */
public abstract class Transformer {

    /** Human-readable name used in logs. */
    public abstract String name();

    /**
     * Apply this transformation to the class represented by {@code ctx}.
     * Modify {@code ctx.classNode()} in-place; the pipeline propagates
     * the mutated node to the next transformer automatically.
     */
    public abstract void transform(TransformContext ctx);
}
