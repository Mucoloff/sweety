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
    private final Map<String, Map<String, String>> globalFieldMappings;

    public FieldOverloadCollisionTransformer() {
        this("a", new HashMap<>());
    }

    public FieldOverloadCollisionTransformer(String basePrefix) {
        this(basePrefix, new HashMap<>());
    }

    public FieldOverloadCollisionTransformer(String basePrefix, Map<String, Map<String, String>> globalFieldMappings) {
        this.basePrefix = basePrefix;
        this.globalFieldMappings = globalFieldMappings != null ? globalFieldMappings : new HashMap<>();
    }

    public Map<String, Map<String, String>> getGlobalFieldMappings() {
        return globalFieldMappings;
    }

    public void registerClass(ClassNode cn) {
        if (cn.fields == null || cn.fields.isEmpty()) return;
        Map<String, String> fieldRemap = globalFieldMappings.computeIfAbsent(cn.name, k -> new HashMap<>());

        Map<String, Integer> descToNameIndex = new HashMap<>();

        for (FieldNode fn : cn.fields) {
            int nameIdx = 0;
            String assignedName = basePrefix;
            if (descToNameIndex.containsKey(fn.desc)) {
                nameIdx = descToNameIndex.get(fn.desc) + 1;
                assignedName = String.valueOf((char) (basePrefix.charAt(0) + nameIdx));
            }
            descToNameIndex.put(fn.desc, nameIdx);
            fieldRemap.put(fn.name + "#" + fn.desc, assignedName);
        }
    }

    @Override
    public String name() {
        return "FieldOverloadCollision";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();
        if (cn.fields != null && !cn.fields.isEmpty()) {
            registerClass(cn);
            Map<String, String> fieldRemap = globalFieldMappings.get(cn.name);
            for (FieldNode fn : cn.fields) {
                String newName = fieldRemap.get(fn.name + "#" + fn.desc);
                if (newName != null) {
                    fn.name = newName;
                }
            }
        }

        // Remap all GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC instructions (intra-class and cross-class)
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof FieldInsnNode finsn) {
                    Map<String, String> remapForOwner = globalFieldMappings.get(finsn.owner);
                    if (remapForOwner != null) {
                        String key = finsn.name + "#" + finsn.desc;
                        String newName = remapForOwner.get(key);
                        if (newName != null) {
                            finsn.name = newName;
                        }
                    }
                }
            }
        }
    }
}
