package dev.sweety.patch.generator.pass;

import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import dev.sweety.patch.ClassPatch;
import dev.sweety.patch.MethodPatch;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;

/**
 * Pass that extracts bytecode inserted at method entry (HEAD).
 */
public final class HeadInjectionPass {

    private HeadInjectionPass() {}

    public static boolean apply(ClassPatch classPatch, MethodNode modMethod, AbstractDelta<String> delta) {
        if (delta.getType() == DeltaType.INSERT && delta.getTarget().getPosition() == 0) {
            final InsnList injectedInsns = cloneInsnSublist(modMethod.instructions, 0, delta.getTarget().size());
            classPatch.patchMethod(MethodPatch.atHead(modMethod.name, modMethod.desc, (mv, version) -> {
                injectedInsns.accept(mv);
            }));
            return true;
        }
        return false;
    }

    private static InsnList cloneInsnSublist(InsnList source, int start, int count) {
        InsnList list = new InsnList();
        int idx = 0;
        for (AbstractInsnNode insn = source.getFirst(); insn != null && idx < (start + count); insn = insn.getNext(), idx++) {
            if (idx >= start) {
                list.add(insn.clone(new HashMap<>()));
            }
        }
        return list;
    }
}
