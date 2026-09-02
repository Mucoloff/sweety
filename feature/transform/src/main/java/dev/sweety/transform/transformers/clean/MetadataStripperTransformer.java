package dev.sweety.transform.transformers.clean;

import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Strips all build-time marker annotations (@Transform, @SecurityCritical, @ProtectPayload, @Virtualize),
 * debug symbols (SourceFile, SourceDebugExtension, LocalVariableTable, LineNumbers),
 * and inner-class / nest-host linkage metadata.
 */
public final class MetadataStripperTransformer extends Transformer {

    @Override
    public String name() {
        return "MetadataStripper";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();

        // 1. Strip debug & source metadata
        cn.sourceFile = null;
        cn.sourceDebug = null;

        // 2. Strip nest host & inner classes linkage
        cn.nestHostClass = null;
        if (cn.nestMembers != null) cn.nestMembers.clear();
        if (cn.innerClasses != null) cn.innerClasses.clear();

        // 3. Strip class-level annotations
        cn.visibleAnnotations = null;
        cn.invisibleAnnotations = null;
        cn.visibleTypeAnnotations = null;
        cn.invisibleTypeAnnotations = null;

        // 4. Strip field annotations
        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                fn.visibleAnnotations = null;
                fn.invisibleAnnotations = null;
                fn.visibleTypeAnnotations = null;
                fn.invisibleTypeAnnotations = null;
            }
        }

        // 5. Strip method annotations & debug symbol tables
        if (cn.methods != null) {
            for (MethodNode mn : cn.methods) {
                mn.visibleAnnotations = null;
                mn.invisibleAnnotations = null;
                mn.visibleTypeAnnotations = null;
                mn.invisibleTypeAnnotations = null;
                mn.visibleParameterAnnotations = null;
                mn.invisibleParameterAnnotations = null;

                // Strip local variable names and line number tables
                mn.localVariables = null;
                mn.parameters = null;
            }
        }
    }
}
