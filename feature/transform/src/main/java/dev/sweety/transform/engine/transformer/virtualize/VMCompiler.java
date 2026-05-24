package dev.sweety.transform.engine.transformer.virtualize;

import dev.sweety.transform.vm.VMOpcode;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Compiles an ASM {@link MethodNode} to a flat {@code byte[]} of
 * {@link VMOpcode} instructions for execution by {@link dev.sweety.transform.vm.VMInterpreter}.
 *
 * <h3>Two-pass label resolution</h3>
 * <ol>
 *   <li>First pass: emit instructions with placeholder offsets (4-byte int = 0xCAFEBABE)
 *       and record the bytecode offset of every {@link LabelNode}.</li>
 *   <li>Second pass: patch all jump operands with the resolved absolute offsets.</li>
 * </ol>
 *
 * <h3>Header</h3>
 * The first 2 bytes of the output are a {@code short} containing {@code maxLocals},
 * so the VM can pre-allocate its local variable array.
 */
public final class VMCompiler {

    private VMCompiler() {}

    /** Sentinel offset written for unresolved labels in pass 1. */
    private static final int UNRESOLVED = 0xCAFEBABE;

    public static byte[] compile(MethodNode mn) {
        try {
            return doCompile(mn);
        } catch (IOException e) {
            throw new RuntimeException("VMCompiler failed for " + mn.name + mn.desc, e);
        }
    }

    private static byte[] doCompile(MethodNode mn) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        final DataOutputStream out = new DataOutputStream(baos);

        // ── Header: maxLocals ─────────────────────────────────────────────────
        out.writeShort(mn.maxLocals + 4); // +4 for VM internal headroom

        // ── Pass 1: emit instructions, record label positions ─────────────────
        // labelOffsets: LabelNode (identity) → absolute byte offset in the output
        final Map<LabelNode, Integer> labelOffsets = new IdentityHashMap<>();
        // jumpPatches: [outputByteOffset, LabelNode] — patched in pass 2
        // Stored as Object[] so we keep a direct reference to the LabelNode,
        // avoiding the System.identityHashCode collision bug that occurred when
        // two distinct LabelNodes shared the same hash code.
        final List<Object[]> jumpPatches = new ArrayList<>();

        for (AbstractInsnNode insn : mn.instructions) {
            if (insn instanceof LabelNode ln) {
                labelOffsets.put(ln, baos.size());
                continue;
            }
            if (insn instanceof LineNumberNode || insn instanceof FrameNode) {
                continue;
            }
            emitInstruction(insn, out, baos, jumpPatches);
        }

        final byte[] raw = baos.toByteArray();

        // ── Pass 2: patch jump targets ────────────────────────────────────────
        for (Object[] patch : jumpPatches) {
            final int       patchPos = (Integer)   patch[0];
            final LabelNode label    = (LabelNode) patch[1];
            final Integer   target   = labelOffsets.get(label);
            if (target == null) {
                throw new IllegalStateException("Unresolved label in VMCompiler for " + mn.name);
            }
            raw[patchPos]     = (byte) (target >>> 24);
            raw[patchPos + 1] = (byte) (target >>> 16);
            raw[patchPos + 2] = (byte) (target >>> 8);
            raw[patchPos + 3] = (byte) ((int) target);
        }

        return raw;
    }

    // ── Instruction emission ──────────────────────────────────────────────────

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private static void emitInstruction(AbstractInsnNode insn, DataOutputStream out,
                                         ByteArrayOutputStream baos,
                                         List<Object[]> patches) throws IOException {
        final int op = insn.getOpcode();

        switch (op) {

            // ── Constants ─────────────────────────────────────────────────────
            case Opcodes.ACONST_NULL -> out.writeByte(VMOpcode.PUSH_NULL);
            case Opcodes.ICONST_M1   -> { out.writeByte(VMOpcode.PUSH_INT); out.writeInt(-1); }
            case Opcodes.ICONST_0    -> { out.writeByte(VMOpcode.PUSH_INT); out.writeInt(0); }
            case Opcodes.ICONST_1    -> { out.writeByte(VMOpcode.PUSH_INT); out.writeInt(1); }
            case Opcodes.ICONST_2    -> { out.writeByte(VMOpcode.PUSH_INT); out.writeInt(2); }
            case Opcodes.ICONST_3    -> { out.writeByte(VMOpcode.PUSH_INT); out.writeInt(3); }
            case Opcodes.ICONST_4    -> { out.writeByte(VMOpcode.PUSH_INT); out.writeInt(4); }
            case Opcodes.ICONST_5    -> { out.writeByte(VMOpcode.PUSH_INT); out.writeInt(5); }
            case Opcodes.LCONST_0    -> { out.writeByte(VMOpcode.PUSH_LONG); out.writeLong(0L); }
            case Opcodes.LCONST_1    -> { out.writeByte(VMOpcode.PUSH_LONG); out.writeLong(1L); }
            case Opcodes.FCONST_0    -> { out.writeByte(VMOpcode.PUSH_FLOAT); out.writeInt(Float.floatToIntBits(0f)); }
            case Opcodes.FCONST_1    -> { out.writeByte(VMOpcode.PUSH_FLOAT); out.writeInt(Float.floatToIntBits(1f)); }
            case Opcodes.FCONST_2    -> { out.writeByte(VMOpcode.PUSH_FLOAT); out.writeInt(Float.floatToIntBits(2f)); }
            case Opcodes.DCONST_0    -> { out.writeByte(VMOpcode.PUSH_DOUBLE); out.writeLong(Double.doubleToLongBits(0.0)); }
            case Opcodes.DCONST_1    -> { out.writeByte(VMOpcode.PUSH_DOUBLE); out.writeLong(Double.doubleToLongBits(1.0)); }
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> { out.writeByte(VMOpcode.PUSH_INT); out.writeInt(((IntInsnNode) insn).operand); }
            case Opcodes.LDC -> emitLdc((LdcInsnNode) insn, out);

            // ── Loads ─────────────────────────────────────────────────────────
            // ASM Tree API always uses the generic ILOAD/LLOAD/etc. with a var field;
            // the xLOAD_n shorthand opcodes are never produced by the Tree API.
            case Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                out.writeByte(VMOpcode.LOAD); out.writeShort(((VarInsnNode) insn).var);
            }

            // ── Stores ────────────────────────────────────────────────────────
            case Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> {
                out.writeByte(VMOpcode.STORE); out.writeShort(((VarInsnNode) insn).var);
            }

            // ── Arithmetic ────────────────────────────────────────────────────
            case Opcodes.IADD -> out.writeByte(VMOpcode.IADD);
            case Opcodes.ISUB -> out.writeByte(VMOpcode.ISUB);
            case Opcodes.IMUL -> out.writeByte(VMOpcode.IMUL);
            case Opcodes.IDIV -> out.writeByte(VMOpcode.IDIV);
            case Opcodes.IREM -> out.writeByte(VMOpcode.IREM);
            case Opcodes.INEG -> out.writeByte(VMOpcode.INEG);
            case Opcodes.IAND -> out.writeByte(VMOpcode.IAND);
            case Opcodes.IOR  -> out.writeByte(VMOpcode.IOR);
            case Opcodes.IXOR -> out.writeByte(VMOpcode.IXOR);
            case Opcodes.ISHL -> out.writeByte(VMOpcode.ISHL);
            case Opcodes.ISHR -> out.writeByte(VMOpcode.ISHR);
            case Opcodes.IUSHR -> out.writeByte(VMOpcode.IUSHR);
            case Opcodes.LADD -> out.writeByte(VMOpcode.LADD);
            case Opcodes.LSUB -> out.writeByte(VMOpcode.LSUB);
            case Opcodes.LMUL -> out.writeByte(VMOpcode.LMUL);
            case Opcodes.LDIV -> out.writeByte(VMOpcode.LDIV);
            case Opcodes.LREM -> out.writeByte(VMOpcode.LREM);
            case Opcodes.LNEG -> out.writeByte(VMOpcode.LNEG);
            case Opcodes.LAND -> out.writeByte(VMOpcode.LAND);
            case Opcodes.LOR  -> out.writeByte(VMOpcode.LOR);
            case Opcodes.LXOR -> out.writeByte(VMOpcode.LXOR);
            case Opcodes.LCMP -> out.writeByte(VMOpcode.LCMP);
            case Opcodes.FADD -> out.writeByte(VMOpcode.FADD);
            case Opcodes.FSUB -> out.writeByte(VMOpcode.FSUB);
            case Opcodes.FMUL -> out.writeByte(VMOpcode.FMUL);
            case Opcodes.FDIV -> out.writeByte(VMOpcode.FDIV);
            case Opcodes.FREM -> out.writeByte(VMOpcode.FREM);
            case Opcodes.FNEG -> out.writeByte(VMOpcode.FNEG);
            case Opcodes.FCMPL -> out.writeByte(VMOpcode.FCMPL);
            case Opcodes.FCMPG -> out.writeByte(VMOpcode.FCMPG);
            case Opcodes.DADD -> out.writeByte(VMOpcode.DADD);
            case Opcodes.DSUB -> out.writeByte(VMOpcode.DSUB);
            case Opcodes.DMUL -> out.writeByte(VMOpcode.DMUL);
            case Opcodes.DDIV -> out.writeByte(VMOpcode.DDIV);
            case Opcodes.DREM -> out.writeByte(VMOpcode.DREM);
            case Opcodes.DNEG -> out.writeByte(VMOpcode.DNEG);
            case Opcodes.DCMPL -> out.writeByte(VMOpcode.DCMPL);
            case Opcodes.DCMPG -> out.writeByte(VMOpcode.DCMPG);

            // ── Conversions ───────────────────────────────────────────────────
            case Opcodes.I2L  -> out.writeByte(VMOpcode.I2L);
            case Opcodes.I2F  -> out.writeByte(VMOpcode.I2F);
            case Opcodes.I2D  -> out.writeByte(VMOpcode.I2D);
            case Opcodes.I2B  -> out.writeByte(VMOpcode.I2B);
            case Opcodes.I2C  -> out.writeByte(VMOpcode.I2C);
            case Opcodes.I2S  -> out.writeByte(VMOpcode.I2S);
            case Opcodes.L2I  -> out.writeByte(VMOpcode.L2I);
            case Opcodes.L2F  -> out.writeByte(VMOpcode.L2F);
            case Opcodes.L2D  -> out.writeByte(VMOpcode.L2D);
            case Opcodes.F2I  -> out.writeByte(VMOpcode.F2I);
            case Opcodes.F2L  -> out.writeByte(VMOpcode.F2L);
            case Opcodes.F2D  -> out.writeByte(VMOpcode.F2D);
            case Opcodes.D2I  -> out.writeByte(VMOpcode.D2I);
            case Opcodes.D2L  -> out.writeByte(VMOpcode.D2L);
            case Opcodes.D2F  -> out.writeByte(VMOpcode.D2F);

            // ── Stack ─────────────────────────────────────────────────────────
            case Opcodes.POP    -> out.writeByte(VMOpcode.POP);
            case Opcodes.POP2   -> out.writeByte(VMOpcode.POP2);
            case Opcodes.DUP    -> out.writeByte(VMOpcode.DUP);
            case Opcodes.DUP_X1 -> out.writeByte(VMOpcode.DUP_X1);
            case Opcodes.DUP_X2 -> out.writeByte(VMOpcode.DUP_X2);
            case Opcodes.DUP2   -> out.writeByte(VMOpcode.DUP2);
            case Opcodes.SWAP   -> out.writeByte(VMOpcode.SWAP);

            // ── Jumps ─────────────────────────────────────────────────────────
            case Opcodes.GOTO      -> emitJump(VMOpcode.GOTO,       insn, out, baos, patches);
            case Opcodes.IFEQ      -> emitJump(VMOpcode.IFEQ,       insn, out, baos, patches);
            case Opcodes.IFNE      -> emitJump(VMOpcode.IFNE,       insn, out, baos, patches);
            case Opcodes.IFLT      -> emitJump(VMOpcode.IFLT,       insn, out, baos, patches);
            case Opcodes.IFGE      -> emitJump(VMOpcode.IFGE,       insn, out, baos, patches);
            case Opcodes.IFGT      -> emitJump(VMOpcode.IFGT,       insn, out, baos, patches);
            case Opcodes.IFLE      -> emitJump(VMOpcode.IFLE,       insn, out, baos, patches);
            case Opcodes.IF_ICMPEQ -> emitJump(VMOpcode.IF_ICMPEQ,  insn, out, baos, patches);
            case Opcodes.IF_ICMPNE -> emitJump(VMOpcode.IF_ICMPNE,  insn, out, baos, patches);
            case Opcodes.IF_ICMPLT -> emitJump(VMOpcode.IF_ICMPLT,  insn, out, baos, patches);
            case Opcodes.IF_ICMPGE -> emitJump(VMOpcode.IF_ICMPGE,  insn, out, baos, patches);
            case Opcodes.IF_ICMPGT -> emitJump(VMOpcode.IF_ICMPGT,  insn, out, baos, patches);
            case Opcodes.IF_ICMPLE -> emitJump(VMOpcode.IF_ICMPLE,  insn, out, baos, patches);
            case Opcodes.IF_ACMPEQ -> emitJump(VMOpcode.IF_ACMPEQ,  insn, out, baos, patches);
            case Opcodes.IF_ACMPNE -> emitJump(VMOpcode.IF_ACMPNE,  insn, out, baos, patches);
            case Opcodes.IFNULL    -> emitJump(VMOpcode.IF_NULL,     insn, out, baos, patches);
            case Opcodes.IFNONNULL -> emitJump(VMOpcode.IF_NONNULL,  insn, out, baos, patches);

            // ── Returns ───────────────────────────────────────────────────────
            case Opcodes.RETURN  -> out.writeByte(VMOpcode.RETURN);
            case Opcodes.IRETURN -> out.writeByte(VMOpcode.IRETURN);
            case Opcodes.LRETURN -> out.writeByte(VMOpcode.LRETURN);
            case Opcodes.FRETURN -> out.writeByte(VMOpcode.FRETURN);
            case Opcodes.DRETURN -> out.writeByte(VMOpcode.DRETURN);
            case Opcodes.ARETURN -> out.writeByte(VMOpcode.ARETURN);
            case Opcodes.ATHROW  -> out.writeByte(VMOpcode.THROW);

            // ── Method invocations ────────────────────────────────────────────
            case Opcodes.INVOKEVIRTUAL -> {
                MethodInsnNode m = (MethodInsnNode) insn;
                out.writeByte(VMOpcode.INVOKE_VIRTUAL);
                writeStr(out, m.owner); writeStr(out, m.name); writeStr(out, m.desc);
            }
            case Opcodes.INVOKESTATIC -> {
                MethodInsnNode m = (MethodInsnNode) insn;
                out.writeByte(VMOpcode.INVOKE_STATIC);
                writeStr(out, m.owner); writeStr(out, m.name); writeStr(out, m.desc);
            }
            case Opcodes.INVOKESPECIAL -> {
                MethodInsnNode m = (MethodInsnNode) insn;
                out.writeByte(VMOpcode.INVOKE_SPECIAL);
                writeStr(out, m.owner); writeStr(out, m.name); writeStr(out, m.desc);
            }
            case Opcodes.INVOKEINTERFACE -> {
                MethodInsnNode m = (MethodInsnNode) insn;
                out.writeByte(VMOpcode.INVOKE_INTERFACE);
                writeStr(out, m.owner); writeStr(out, m.name); writeStr(out, m.desc);
            }

            // ── Field access ──────────────────────────────────────────────────
            case Opcodes.GETFIELD -> {
                FieldInsnNode f = (FieldInsnNode) insn;
                out.writeByte(VMOpcode.GET_FIELD);
                writeStr(out, f.owner); writeStr(out, f.name); writeStr(out, f.desc);
            }
            case Opcodes.PUTFIELD -> {
                FieldInsnNode f = (FieldInsnNode) insn;
                out.writeByte(VMOpcode.PUT_FIELD);
                writeStr(out, f.owner); writeStr(out, f.name); writeStr(out, f.desc);
            }
            case Opcodes.GETSTATIC -> {
                FieldInsnNode f = (FieldInsnNode) insn;
                out.writeByte(VMOpcode.GET_STATIC);
                writeStr(out, f.owner); writeStr(out, f.name); writeStr(out, f.desc);
            }
            case Opcodes.PUTSTATIC -> {
                FieldInsnNode f = (FieldInsnNode) insn;
                out.writeByte(VMOpcode.PUT_STATIC);
                writeStr(out, f.owner); writeStr(out, f.name); writeStr(out, f.desc);
            }

            // ── Object / array operations ─────────────────────────────────────
            case Opcodes.NEW -> {
                out.writeByte(VMOpcode.NEW); writeStr(out, ((TypeInsnNode) insn).desc);
            }
            case Opcodes.CHECKCAST -> {
                out.writeByte(VMOpcode.CHECKCAST); writeStr(out, ((TypeInsnNode) insn).desc);
            }
            case Opcodes.INSTANCEOF -> {
                out.writeByte(VMOpcode.INSTANCEOF); writeStr(out, ((TypeInsnNode) insn).desc);
            }
            case Opcodes.ARRAYLENGTH  -> out.writeByte(VMOpcode.ARRAYLENGTH);
            case Opcodes.MONITORENTER -> out.writeByte(VMOpcode.MONENTER);
            case Opcodes.MONITOREXIT  -> out.writeByte(VMOpcode.MONEXIT);
            case Opcodes.AALOAD  -> out.writeByte(VMOpcode.AALOAD);
            case Opcodes.AASTORE -> out.writeByte(VMOpcode.AASTORE);
            case Opcodes.IALOAD  -> out.writeByte(VMOpcode.IALOAD);
            case Opcodes.IASTORE -> out.writeByte(VMOpcode.IASTORE);
            case Opcodes.LALOAD  -> out.writeByte(VMOpcode.LALOAD);
            case Opcodes.LASTORE -> out.writeByte(VMOpcode.LASTORE);
            case Opcodes.FALOAD  -> out.writeByte(VMOpcode.FALOAD);
            case Opcodes.FASTORE -> out.writeByte(VMOpcode.FASTORE);
            case Opcodes.DALOAD  -> out.writeByte(VMOpcode.DALOAD);
            case Opcodes.DASTORE -> out.writeByte(VMOpcode.DASTORE);
            case Opcodes.BALOAD  -> out.writeByte(VMOpcode.BALOAD);
            case Opcodes.BASTORE -> out.writeByte(VMOpcode.BASTORE);
            case Opcodes.CALOAD  -> out.writeByte(VMOpcode.CALOAD);
            case Opcodes.CASTORE -> out.writeByte(VMOpcode.CASTORE);
            case Opcodes.SALOAD  -> out.writeByte(VMOpcode.SALOAD);
            case Opcodes.SASTORE -> out.writeByte(VMOpcode.SASTORE);
            case Opcodes.NEWARRAY -> {
                out.writeByte(VMOpcode.NEWARRAY);
                out.writeByte(((IntInsnNode) insn).operand);
            }
            case Opcodes.ANEWARRAY -> {
                out.writeByte(VMOpcode.ANEWARRAY);
                writeStr(out, ((TypeInsnNode) insn).desc);
            }
            case Opcodes.IINC -> {
                // IINC var, increment → LOAD var; PUSH_INT inc; IADD; STORE var
                IincInsnNode iinc = (IincInsnNode) insn;
                out.writeByte(VMOpcode.LOAD);  out.writeShort(iinc.var);
                out.writeByte(VMOpcode.PUSH_INT); out.writeInt(iinc.incr);
                out.writeByte(VMOpcode.IADD);
                out.writeByte(VMOpcode.STORE); out.writeShort(iinc.var);
            }

            default -> throw new UnsupportedOperationException(
                    "Unsupported JVM opcode for virtualization: " + op
                    + " (" + (insn.getClass().getSimpleName()) + ")");
        }
    }

    private static void emitLdc(LdcInsnNode insn, DataOutputStream out) throws IOException {
        Object cst = insn.cst;
        if (cst instanceof Integer i)  { out.writeByte(VMOpcode.PUSH_INT);    out.writeInt(i); }
        else if (cst instanceof Long l)  { out.writeByte(VMOpcode.PUSH_LONG);   out.writeLong(l); }
        else if (cst instanceof Float f) { out.writeByte(VMOpcode.PUSH_FLOAT);  out.writeInt(Float.floatToIntBits(f)); }
        else if (cst instanceof Double d){ out.writeByte(VMOpcode.PUSH_DOUBLE); out.writeLong(Double.doubleToLongBits(d)); }
        else if (cst instanceof String s){ out.writeByte(VMOpcode.PUSH_STRING); writeStr(out, s); }
        else if (cst instanceof Type t)  { out.writeByte(VMOpcode.PUSH_CLASS);  writeStr(out, t.getInternalName()); }
        else throw new UnsupportedOperationException("Unsupported LDC constant type: " + cst.getClass());
    }

    private static void emitJump(byte vmOp, AbstractInsnNode insn, DataOutputStream out,
                                   ByteArrayOutputStream baos, List<Object[]> patches) throws IOException {
        final LabelNode label = ((JumpInsnNode) insn).label;
        out.writeByte(vmOp);
        final int patchPos = baos.size();
        out.writeInt(UNRESOLVED);
        // Store direct reference — avoids System.identityHashCode collision
        patches.add(new Object[]{patchPos, label});
    }

    private static void writeStr(DataOutputStream out, String s) throws IOException {
        final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }
}
