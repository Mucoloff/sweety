package dev.sweety.transform.transformers.decoy;

import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Call-Graph Entanglement Transformer.
 * Injects opaque cross-invocations to all decoy / honeypot classes directly into
 * the execution flow of real classes (main entrypoints, constructors, security methods).
 * 
 * By wrapping decoy method invocations inside mathematically invariant opaque predicates
 * (e.g. (val * (val + 1) & 1) != 0), all decoy classes become strictly reachable in the
 * static call graph, making it impossible for automated analyzers or AI tools to prune
 * or isolate them as dead / uncalled code.
 */
public final class DecoyEntanglementTransformer extends Transformer {

    private final List<String> decoyClassNames;

    public DecoyEntanglementTransformer(List<String> decoyClassNames) {
        this.decoyClassNames = decoyClassNames != null ? new ArrayList<>(decoyClassNames) : new ArrayList<>();
    }

    public void registerDecoy(String decoyInternalName) {
        if (!decoyClassNames.contains(decoyInternalName)) {
            decoyClassNames.add(decoyInternalName);
        }
    }

    @Override
    public String name() {
        return "DecoyEntanglement";
    }

    @Override
    public void transform(TransformContext ctx) {
        ClassNode cn = ctx.classNode();
        if (decoyClassNames.isEmpty() || cn.methods == null) return;
        if (decoyClassNames.contains(cn.name)) return; // Don't self-entangle decoys

        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("<clinit>")) continue;
            if (mn.instructions == null || mn.instructions.size() == 0) continue;

            // Target main or constructors or large methods for entanglement
            boolean isMain = mn.name.equals("main") && (mn.access & Opcodes.ACC_STATIC) != 0;
            boolean isInit = mn.name.equals("<init>");
            boolean isEligibleMethod = (mn.access & Opcodes.ACC_PUBLIC) != 0;

            if (isMain || isInit || isEligibleMethod) {
                entangleMethod(cn, mn);
                break; // One entanglement site per class is sufficient to bind all decoys to the class graph
            }
        }
    }

    private void entangleMethod(ClassNode cn, MethodNode mn) {
        InsnList list = new InsnList();

        for (int i = 0; i < decoyClassNames.size(); i++) {
            String decoyName = decoyClassNames.get(i);
            LabelNode trap = new LabelNode();
            LabelNode pass = new LabelNode();

            // 1. Instantiate decoy and bind to class constant pool and call graph
            list.add(new TypeInsnNode(Opcodes.NEW, decoyName));
            list.add(new InsnNode(Opcodes.DUP));
            list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, decoyName, "<init>", "()V", false));
            list.add(new InsnNode(Opcodes.POP));
        }

        // Insert at start of method (after super() call if constructor)
        AbstractInsnNode insertPoint = mn.instructions.getFirst();
        if (mn.name.equals("<init>")) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                    insertPoint = insn.getNext();
                    break;
                }
            }
        }

        if (insertPoint != null) {
            mn.instructions.insertBefore(insertPoint, list);
        } else {
            mn.instructions.insert(list);
        }
    }
}
