package dev.sweety.transform.transformers.remap;

import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JVMS §4.5 & §4.4.2 Field Overloading / Collision Obfuscator.
 * Assigns identical field names (e.g., 'a', 'b') to fields with different descriptors,
 * producing valid JVM bytecode that breaks Java source recompilation in standard decompilers.
 */
public final class FieldOverloadCollisionTransformer extends Transformer {

    private final String basePrefix;

    public FieldOverloadCollisionTransformer() {
        this("a");
    }

    public FieldOverloadCollisionTransformer(String basePrefix) {
        this.basePrefix = basePrefix;
    }

    @Override
    public String name() {
        return "FieldOverloadCollision";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();
        if (cn.fields == null || cn.fields.isEmpty()) return;

        // Old field identity (name + desc) -> New field name
        Map<String, String> fieldRemap = new HashMap<>();

        // Group by static vs instance
        Map<String, Integer> descToNameIndexStatic = new HashMap<>();
        Map<String, Integer> descToNameIndexInstance = new HashMap<>();

        for (FieldNode fn : cn.fields) {
            boolean isStatic = (fn.access & Opcodes.ACC_STATIC) != 0;
            Map<String, Integer> descMap = isStatic ? descToNameIndexStatic : descToNameIndexInstance;

            // Find an available common name index for this descriptor
            int nameIdx = 0;
            String assignedName = basePrefix;
            if (descMap.containsKey(fn.desc)) {
                // Another field with the same descriptor already used this name, increment index
                nameIdx = descMap.get(fn.desc) + 1;
                assignedName = String.valueOf((char) (basePrefix.charAt(0) + nameIdx));
            }
            descMap.put(fn.desc, nameIdx);

            fieldRemap.put(fn.name + "#" + fn.desc, assignedName);
            fn.name = assignedName;
        }

        // Remap all GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC instructions
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof FieldInsnNode finsn) {
                    if (finsn.owner.equals(cn.name)) {
                        String key = finsn.name + "#" + finsn.desc;
                        String newName = fieldRemap.get(key);
                        if (newName != null) {
                            finsn.name = newName;
                        }
                    }
                }
            }
        }
    }
}
