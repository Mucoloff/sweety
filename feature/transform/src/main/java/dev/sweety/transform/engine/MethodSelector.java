package dev.sweety.transform.engine;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

/**
 * Determines which methods should be transformed or virtualized based on
 * {@code @Transform} / {@code @Virtualize} annotations present in the bytecode.
 */
public final class MethodSelector {

    private static final String TRANSFORM_DESC   = "Ldev/sweety/transform/annotation/Transform;";
    private static final String VIRTUALIZE_DESC  = "Ldev/sweety/transform/annotation/Virtualize;";

    private MethodSelector() {}

    /** True if the class or method carries {@code @Transform} (directly or via superclass). */
    public static boolean shouldTransform(TransformContext ctx, MethodNode mn) {
        final ClassNode cn = ctx.classNode();
        return hasAnnotation(mn.invisibleAnnotations, TRANSFORM_DESC)
                || hasAnnotation(mn.visibleAnnotations, TRANSFORM_DESC)
                || hasAnnotation(cn.invisibleAnnotations, TRANSFORM_DESC)
                || hasAnnotation(cn.visibleAnnotations, TRANSFORM_DESC)
                || ctx.hasSuperTransform();
    }

    /** @deprecated use {@link #shouldTransform(TransformContext, MethodNode)} */
    @Deprecated
    public static boolean shouldTransform(ClassNode cn, MethodNode mn) {
        return hasAnnotation(mn.invisibleAnnotations, TRANSFORM_DESC)
                || hasAnnotation(mn.visibleAnnotations, TRANSFORM_DESC)
                || hasAnnotation(cn.invisibleAnnotations, TRANSFORM_DESC)
                || hasAnnotation(cn.visibleAnnotations, TRANSFORM_DESC);
    }

    /** True if the method carries {@code @Virtualize}. */
    public static boolean shouldVirtualize(MethodNode mn) {
        return hasAnnotation(mn.invisibleAnnotations, VIRTUALIZE_DESC)
                || hasAnnotation(mn.visibleAnnotations, VIRTUALIZE_DESC);
    }

    /** True if the method is safe to transform (not native, not abstract, has code). */
    public static boolean isEligible(MethodNode mn) {
        if ((mn.access & Opcodes.ACC_NATIVE)   != 0) return false;
        if ((mn.access & Opcodes.ACC_ABSTRACT) != 0) return false;
        return mn.instructions != null && mn.instructions.size() != 0;
    }

    /**
     * True if the method is safe to virtualize (no JSR, no synchronized, no exception handlers).
     * VMInterpreter has no handler-table dispatch — a thrown exception during VM execution always
     * propagates straight out of the interpreter loop (see ObjectOps.executeThrow), never routing into
     * a compiled catch/finally block. Compiling a method with a non-empty exception table anyway would
     * silently miscompile (the handler range would just be treated as ordinary unreachable-by-fallthrough
     * code), so reject it outright rather than produce a corrupt method.
     */
    public static boolean isVirtualizable(MethodNode mn) {
        if (!isEligible(mn)) return false;
        if ((mn.access & Opcodes.ACC_SYNCHRONIZED) != 0) return false;
        if (mn.tryCatchBlocks != null && !mn.tryCatchBlocks.isEmpty()) return false;
        // JSR/RET check
        for (var insn : mn.instructions) {
            if (insn.getOpcode() == Opcodes.JSR || insn.getOpcode() == Opcodes.RET) return false;
        }
        return true;
    }

    /**
     * Read the {@code exceptionFlow} element from the {@code @Transform} annotation.
     * Returns false if the annotation is absent.
     */
    public static boolean exceptionFlow(ClassNode cn, MethodNode mn) {
        return getBooleanValue(mn.invisibleAnnotations, TRANSFORM_DESC, "exceptionFlow")
                || getBooleanValue(mn.visibleAnnotations, TRANSFORM_DESC, "exceptionFlow")
                || getBooleanValue(cn.invisibleAnnotations, TRANSFORM_DESC, "exceptionFlow")
                || getBooleanValue(cn.visibleAnnotations, TRANSFORM_DESC, "exceptionFlow");
    }

    public static boolean transformStrings(ClassNode cn, MethodNode mn) {
        Boolean v = getBooleanValueNullable(mn.invisibleAnnotations, TRANSFORM_DESC, "strings");
        if (v == null) v = getBooleanValueNullable(cn.invisibleAnnotations, TRANSFORM_DESC, "strings");
        return v == null || v; // default true
    }

    public static boolean transformIntegers(ClassNode cn, MethodNode mn) {
        return getBooleanValue(mn.invisibleAnnotations, TRANSFORM_DESC, "integers")
                || getBooleanValue(cn.invisibleAnnotations, TRANSFORM_DESC, "integers");
    }

    /** Strip both @Transform and @Virtualize from the method after processing. */
    public static void stripAnnotations(MethodNode mn) {
        stripFrom(mn.invisibleAnnotations);
        stripFrom(mn.visibleAnnotations);
    }

    /** Strip @Transform from the class itself. */
    public static void stripClassAnnotations(ClassNode cn) {
        stripFrom(cn.invisibleAnnotations);
        stripFrom(cn.visibleAnnotations);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean hasAnnotation(List<AnnotationNode> list, String desc) {
        if (list == null) return false;
        return list.stream().anyMatch(a -> desc.equals(a.desc));
    }

    private static boolean getBooleanValue(List<AnnotationNode> list, String desc, String element) {
        Boolean v = getBooleanValueNullable(list, desc, element);
        return v != null && v;
    }

    private static Boolean getBooleanValueNullable(List<AnnotationNode> list, String desc, String element) {
        if (list == null) return null;
        for (AnnotationNode a : list) {
            if (!desc.equals(a.desc)) continue;
            if (a.values == null) return null;
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                if (element.equals(a.values.get(i))) {
                    return (Boolean) a.values.get(i + 1);
                }
            }
        }
        return null;
    }

    private static void stripFrom(List<AnnotationNode> list) {
        if (list == null) return;
        list.removeIf(a -> TRANSFORM_DESC.equals(a.desc) || VIRTUALIZE_DESC.equals(a.desc));
    }
}
