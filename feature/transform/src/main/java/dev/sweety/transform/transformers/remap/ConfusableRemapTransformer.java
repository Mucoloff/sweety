package dev.sweety.transform.transformers.remap;

import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Remaps private and synthetic helper methods using homoglyph/confusable character sequences.
 */
public final class ConfusableRemapTransformer extends Transformer {

    private final ConfusableDictionary dictionary;
    private final int nameLength;

    public ConfusableRemapTransformer() {
        this(ConfusableDictionary.ILL, 8);
    }

    public ConfusableRemapTransformer(ConfusableDictionary dictionary, int nameLength) {
        this.dictionary = dictionary;
        this.nameLength = nameLength;
    }

    @Override
    public String name() {
        return "ConfusableRemap";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();
        Map<String, String> methodRemap = new HashMap<>();

        int idx = 0;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("<init>") || mn.name.equals("<clinit>") || mn.name.equals("main")) continue;
            boolean isPrivate = (mn.access & Opcodes.ACC_PRIVATE) != 0;
            boolean isSynthetic = (mn.access & Opcodes.ACC_SYNTHETIC) != 0;
            boolean isRoutine = mn.name.startsWith("routine_");

            if (isPrivate || isSynthetic || isRoutine) {
                String newName = ConfusableNameGenerator.generate(idx++, dictionary, nameLength);
                methodRemap.put(mn.name + mn.desc, newName);
                mn.name = newName;
            }
        }

        if (methodRemap.isEmpty()) return;

        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof MethodInsnNode minsn) {
                    if (minsn.owner.equals(cn.name)) {
                        String newName = methodRemap.get(minsn.name + minsn.desc);
                        if (newName != null) {
                            minsn.name = newName;
                        }
                    }
                }
            }
        }
    }
}
