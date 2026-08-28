package dev.sweety.transform.engine.transformer.virtualize;

import dev.sweety.transform.vm.VmOp;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles an ASM {@link MethodNode} to a flat {@code byte[]} of
 * {@link VmOp} instructions for execution by {@link dev.sweety.transform.vm.VMInterpreter}.
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
 *
 * <h3>Category-1/category-2 stack ops</h3>
 * The VM's operand stack is one boxed slot per JVM value (not the real JVM's slot-width-based
 * stack), so {@code DUP2}/{@code DUP_X2}/{@code POP2} can't be interpreted correctly at runtime
 * without knowing whether the real bytecode meant "two category-1 values" or "one category-2 value"
 * (long/double). This compiler resolves that ambiguity once, here, via {@link Analyzer} frame
 * analysis, and emits the matching {@code _CAT1}/{@code _CAT2} {@link VmOp} variant — the
 * interpreter never has to guess.
 */
public final class VMCompiler {

    private VMCompiler() {}

    /** Sentinel offset written for unresolved labels in pass 1. */
    private static final int UNRESOLVED = 0xCAFEBABE;

    public static byte[] compile(String ownerInternalName, MethodNode mn) {
        try {
            return doCompile(ownerInternalName, mn);
        } catch (IOException | AnalyzerException e) {
            throw new RuntimeException("VMCompiler failed for " + mn.name + mn.desc, e);
        }
    }

    private static byte[] doCompile(String ownerInternalName, MethodNode mn) throws IOException, AnalyzerException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        final DataOutputStream out = new DataOutputStream(baos);

        // ── Header: maxLocals + method descriptor ─────────────────────────────
        // The descriptor lets the VM place arguments at the correct JVM local slots:
        // long/double parameters occupy TWO slots, so a naive one-slot-per-arg layout
        // misreads every subsequent local. The VM parses this to compute slot widths.
        out.writeShort(mn.maxLocals + 4); // +4 for VM internal headroom
        writeStr(out, mn.desc);
        // Per-method random salt for the session-fold entanglement check (VMInterpreter reads this
        // once at entry and folds it against the live session keystream, when one is bound).
        out.writeInt(java.util.concurrent.ThreadLocalRandom.current().nextInt());

        // Frame analysis: only needed to disambiguate DUP2/DUP_X2/POP2 category shape.
        // BasicInterpreter tracks value SIZE (1 or 2 words) without resolving real class
        // hierarchies, so a plain owner name (no classloading) is sufficient here.
        final Frame<BasicValue>[] frames = new Analyzer<>(new BasicInterpreter())
                .analyze(ownerInternalName, mn);

        // ── Pass 1: emit instructions, record label positions ─────────────────
        final Reference2IntOpenHashMap<LabelNode> labelOffsets = new Reference2IntOpenHashMap<>();
        labelOffsets.defaultReturnValue(-1);
        // jumpPatches: [outputByteOffset, LabelNode] — patched in pass 2. Stored as Object[] so we
        // keep a direct reference to the LabelNode, avoiding identity-hash collisions.
        final List<Object[]> jumpPatches = new ArrayList<>();

        int insnIndex = 0;
        for (AbstractInsnNode insn : mn.instructions) {
            if (insn instanceof LabelNode ln) {
                labelOffsets.put(ln, baos.size());
                insnIndex++;
                continue;
            }
            if (insn instanceof LineNumberNode || insn instanceof FrameNode) {
                insnIndex++;
                continue;
            }
            emitInstruction(insn, frames[insnIndex], out, baos, jumpPatches);
            insnIndex++;
        }

        final byte[] raw = baos.toByteArray();

        // ── Pass 2: patch jump targets ────────────────────────────────────────
        for (Object[] patch : jumpPatches) {
            final int       patchPos = (Integer)   patch[0];
            final LabelNode label    = (LabelNode) patch[1];
            final int       target   = labelOffsets.getInt(label);
            if (target < 0) {
                throw new IllegalStateException("Unresolved label in VMCompiler for " + mn.name);
            }
            raw[patchPos]     = (byte) (target >>> 24);
            raw[patchPos + 1] = (byte) (target >>> 16);
            raw[patchPos + 2] = (byte) (target >>> 8);
            raw[patchPos + 3] = (byte) target;
        }

        return raw;
    }

    // ── Instruction emission ──────────────────────────────────────────────────

    @SuppressWarnings("DuplicateBranchesInSwitch")
    private static void emitInstruction(AbstractInsnNode insn, Frame<BasicValue> frameBefore,
                                         DataOutputStream out, ByteArrayOutputStream baos,
                                         List<Object[]> patches) throws IOException {
        final int op = insn.getOpcode();

        switch (op) {

            // ── Constants ─────────────────────────────────────────────────────
            case Opcodes.ACONST_NULL -> writeOp(out, VmOp.PUSH_NULL);
            case Opcodes.ICONST_M1   -> { writeOp(out, VmOp.PUSH_INT); out.writeInt(-1); }
            case Opcodes.ICONST_0    -> { writeOp(out, VmOp.PUSH_INT); out.writeInt(0); }
            case Opcodes.ICONST_1    -> { writeOp(out, VmOp.PUSH_INT); out.writeInt(1); }
            case Opcodes.ICONST_2    -> { writeOp(out, VmOp.PUSH_INT); out.writeInt(2); }
            case Opcodes.ICONST_3    -> { writeOp(out, VmOp.PUSH_INT); out.writeInt(3); }
            case Opcodes.ICONST_4    -> { writeOp(out, VmOp.PUSH_INT); out.writeInt(4); }
            case Opcodes.ICONST_5    -> { writeOp(out, VmOp.PUSH_INT); out.writeInt(5); }
            case Opcodes.LCONST_0    -> { writeOp(out, VmOp.PUSH_LONG); out.writeLong(0L); }
            case Opcodes.LCONST_1    -> { writeOp(out, VmOp.PUSH_LONG); out.writeLong(1L); }
            case Opcodes.FCONST_0    -> { writeOp(out, VmOp.PUSH_FLOAT); out.writeInt(Float.floatToIntBits(0f)); }
            case Opcodes.FCONST_1    -> { writeOp(out, VmOp.PUSH_FLOAT); out.writeInt(Float.floatToIntBits(1f)); }
            case Opcodes.FCONST_2    -> { writeOp(out, VmOp.PUSH_FLOAT); out.writeInt(Float.floatToIntBits(2f)); }
            case Opcodes.DCONST_0    -> { writeOp(out, VmOp.PUSH_DOUBLE); out.writeLong(Double.doubleToLongBits(0.0)); }
            case Opcodes.DCONST_1    -> { writeOp(out, VmOp.PUSH_DOUBLE); out.writeLong(Double.doubleToLongBits(1.0)); }
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> { writeOp(out, VmOp.PUSH_INT); out.writeInt(((IntInsnNode) insn).operand); }
            case Opcodes.LDC -> emitLdc((LdcInsnNode) insn, out);

            // ── Loads ─────────────────────────────────────────────────────────
            // ASM Tree API always uses the generic ILOAD/LLOAD/etc. with a var field;
            // the xLOAD_n shorthand opcodes are never produced by the Tree API. The real opcode
            // already distinguishes primitive (I/L/F/D) from reference (A) loads/stores — split here
            // so VMLocals routes to its `prim`/`ref` array with zero runtime type check (de-boxed VM).
            case Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD ->
                    { writeOp(out, VmOp.LOAD_PRIM); out.writeShort(((VarInsnNode) insn).var); }
            case Opcodes.ALOAD ->
                    { writeOp(out, VmOp.LOAD_REF); out.writeShort(((VarInsnNode) insn).var); }

            // ── Stores ────────────────────────────────────────────────────────
            case Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE ->
                    { writeOp(out, VmOp.STORE_PRIM); out.writeShort(((VarInsnNode) insn).var); }
            case Opcodes.ASTORE ->
                    { writeOp(out, VmOp.STORE_REF); out.writeShort(((VarInsnNode) insn).var); }

            // ── Arithmetic ────────────────────────────────────────────────────
            case Opcodes.IADD -> writeOp(out, VmOp.IADD);
            case Opcodes.ISUB -> writeOp(out, VmOp.ISUB);
            case Opcodes.IMUL -> writeOp(out, VmOp.IMUL);
            case Opcodes.IDIV -> writeOp(out, VmOp.IDIV);
            case Opcodes.IREM -> writeOp(out, VmOp.IREM);
            case Opcodes.INEG -> writeOp(out, VmOp.INEG);
            case Opcodes.IAND -> writeOp(out, VmOp.IAND);
            case Opcodes.IOR  -> writeOp(out, VmOp.IOR);
            case Opcodes.IXOR -> writeOp(out, VmOp.IXOR);
            case Opcodes.ISHL -> writeOp(out, VmOp.ISHL);
            case Opcodes.ISHR -> writeOp(out, VmOp.ISHR);
            case Opcodes.IUSHR -> writeOp(out, VmOp.IUSHR);
            case Opcodes.LADD -> writeOp(out, VmOp.LADD);
            case Opcodes.LSUB -> writeOp(out, VmOp.LSUB);
            case Opcodes.LMUL -> writeOp(out, VmOp.LMUL);
            case Opcodes.LDIV -> writeOp(out, VmOp.LDIV);
            case Opcodes.LREM -> writeOp(out, VmOp.LREM);
            case Opcodes.LNEG -> writeOp(out, VmOp.LNEG);
            case Opcodes.LAND -> writeOp(out, VmOp.LAND);
            case Opcodes.LOR  -> writeOp(out, VmOp.LOR);
            case Opcodes.LXOR -> writeOp(out, VmOp.LXOR);
            case Opcodes.LCMP -> writeOp(out, VmOp.LCMP);
            case Opcodes.FADD -> writeOp(out, VmOp.FADD);
            case Opcodes.FSUB -> writeOp(out, VmOp.FSUB);
            case Opcodes.FMUL -> writeOp(out, VmOp.FMUL);
            case Opcodes.FDIV -> writeOp(out, VmOp.FDIV);
            case Opcodes.FREM -> writeOp(out, VmOp.FREM);
            case Opcodes.FNEG -> writeOp(out, VmOp.FNEG);
            case Opcodes.FCMPL -> writeOp(out, VmOp.FCMPL);
            case Opcodes.FCMPG -> writeOp(out, VmOp.FCMPG);
            case Opcodes.DADD -> writeOp(out, VmOp.DADD);
            case Opcodes.DSUB -> writeOp(out, VmOp.DSUB);
            case Opcodes.DMUL -> writeOp(out, VmOp.DMUL);
            case Opcodes.DDIV -> writeOp(out, VmOp.DDIV);
            case Opcodes.DREM -> writeOp(out, VmOp.DREM);
            case Opcodes.DNEG -> writeOp(out, VmOp.DNEG);
            case Opcodes.DCMPL -> writeOp(out, VmOp.DCMPL);
            case Opcodes.DCMPG -> writeOp(out, VmOp.DCMPG);

            // ── Conversions ───────────────────────────────────────────────────
            case Opcodes.I2L  -> writeOp(out, VmOp.I2L);
            case Opcodes.I2F  -> writeOp(out, VmOp.I2F);
            case Opcodes.I2D  -> writeOp(out, VmOp.I2D);
            case Opcodes.I2B  -> writeOp(out, VmOp.I2B);
            case Opcodes.I2C  -> writeOp(out, VmOp.I2C);
            case Opcodes.I2S  -> writeOp(out, VmOp.I2S);
            case Opcodes.L2I  -> writeOp(out, VmOp.L2I);
            case Opcodes.L2F  -> writeOp(out, VmOp.L2F);
            case Opcodes.L2D  -> writeOp(out, VmOp.L2D);
            case Opcodes.F2I  -> writeOp(out, VmOp.F2I);
            case Opcodes.F2L  -> writeOp(out, VmOp.F2L);
            case Opcodes.F2D  -> writeOp(out, VmOp.F2D);
            case Opcodes.D2I  -> writeOp(out, VmOp.D2I);
            case Opcodes.D2L  -> writeOp(out, VmOp.D2L);
            case Opcodes.D2F  -> writeOp(out, VmOp.D2F);

            // ── Stack ─────────────────────────────────────────────────────────
            case Opcodes.POP    -> writeOp(out, VmOp.POP);
            case Opcodes.POP2   -> writeOp(out, pop2Variant(frameBefore));
            case Opcodes.DUP    -> writeOp(out, VmOp.DUP);
            case Opcodes.DUP_X1 -> writeOp(out, VmOp.DUP_X1);
            case Opcodes.DUP_X2 -> writeOp(out, dupX2Variant(frameBefore));
            case Opcodes.DUP2   -> writeOp(out, dup2Variant(frameBefore));
            case Opcodes.SWAP   -> writeOp(out, VmOp.SWAP);

            // ── Jumps ─────────────────────────────────────────────────────────
            case Opcodes.GOTO      -> emitJump(VmOp.GOTO,       insn, out, baos, patches);
            case Opcodes.IFEQ      -> emitJump(VmOp.IFEQ,       insn, out, baos, patches);
            case Opcodes.IFNE      -> emitJump(VmOp.IFNE,       insn, out, baos, patches);
            case Opcodes.IFLT      -> emitJump(VmOp.IFLT,       insn, out, baos, patches);
            case Opcodes.IFGE      -> emitJump(VmOp.IFGE,       insn, out, baos, patches);
            case Opcodes.IFGT      -> emitJump(VmOp.IFGT,       insn, out, baos, patches);
            case Opcodes.IFLE      -> emitJump(VmOp.IFLE,       insn, out, baos, patches);
            case Opcodes.IF_ICMPEQ -> emitJump(VmOp.IF_ICMPEQ,  insn, out, baos, patches);
            case Opcodes.IF_ICMPNE -> emitJump(VmOp.IF_ICMPNE,  insn, out, baos, patches);
            case Opcodes.IF_ICMPLT -> emitJump(VmOp.IF_ICMPLT,  insn, out, baos, patches);
            case Opcodes.IF_ICMPGE -> emitJump(VmOp.IF_ICMPGE,  insn, out, baos, patches);
            case Opcodes.IF_ICMPGT -> emitJump(VmOp.IF_ICMPGT,  insn, out, baos, patches);
            case Opcodes.IF_ICMPLE -> emitJump(VmOp.IF_ICMPLE,  insn, out, baos, patches);
            case Opcodes.IF_ACMPEQ -> emitJump(VmOp.IF_ACMPEQ,  insn, out, baos, patches);
            case Opcodes.IF_ACMPNE -> emitJump(VmOp.IF_ACMPNE,  insn, out, baos, patches);
            case Opcodes.IFNULL    -> emitJump(VmOp.IF_NULL,    insn, out, baos, patches);
            case Opcodes.IFNONNULL -> emitJump(VmOp.IF_NONNULL, insn, out, baos, patches);

            // ── Returns ───────────────────────────────────────────────────────
            case Opcodes.RETURN  -> writeOp(out, VmOp.RETURN);
            case Opcodes.IRETURN -> writeOp(out, VmOp.IRETURN);
            case Opcodes.LRETURN -> writeOp(out, VmOp.LRETURN);
            case Opcodes.FRETURN -> writeOp(out, VmOp.FRETURN);
            case Opcodes.DRETURN -> writeOp(out, VmOp.DRETURN);
            case Opcodes.ARETURN -> writeOp(out, VmOp.ARETURN);
            case Opcodes.ATHROW  -> writeOp(out, VmOp.THROW);

            // ── Method invocations ────────────────────────────────────────────
            case Opcodes.INVOKEVIRTUAL -> {
                MethodInsnNode m = (MethodInsnNode) insn;
                writeOp(out, VmOp.INVOKE_VIRTUAL);
                writeStr(out, m.owner); writeStr(out, m.name); writeStr(out, m.desc);
            }
            case Opcodes.INVOKESTATIC -> {
                MethodInsnNode m = (MethodInsnNode) insn;
                writeOp(out, VmOp.INVOKE_STATIC);
                writeStr(out, m.owner); writeStr(out, m.name); writeStr(out, m.desc);
            }
            case Opcodes.INVOKESPECIAL -> {
                MethodInsnNode m = (MethodInsnNode) insn;
                writeOp(out, VmOp.INVOKE_SPECIAL);
                writeStr(out, m.owner); writeStr(out, m.name); writeStr(out, m.desc);
            }
            case Opcodes.INVOKEINTERFACE -> {
                MethodInsnNode m = (MethodInsnNode) insn;
                writeOp(out, VmOp.INVOKE_INTERFACE);
                writeStr(out, m.owner); writeStr(out, m.name); writeStr(out, m.desc);
            }

            // ── Field access ──────────────────────────────────────────────────
            case Opcodes.GETFIELD -> {
                FieldInsnNode f = (FieldInsnNode) insn;
                writeOp(out, VmOp.GET_FIELD);
                writeStr(out, f.owner); writeStr(out, f.name); writeStr(out, f.desc);
            }
            case Opcodes.PUTFIELD -> {
                FieldInsnNode f = (FieldInsnNode) insn;
                writeOp(out, VmOp.PUT_FIELD);
                writeStr(out, f.owner); writeStr(out, f.name); writeStr(out, f.desc);
            }
            case Opcodes.GETSTATIC -> {
                FieldInsnNode f = (FieldInsnNode) insn;
                writeOp(out, VmOp.GET_STATIC);
                writeStr(out, f.owner); writeStr(out, f.name); writeStr(out, f.desc);
            }
            case Opcodes.PUTSTATIC -> {
                FieldInsnNode f = (FieldInsnNode) insn;
                writeOp(out, VmOp.PUT_STATIC);
                writeStr(out, f.owner); writeStr(out, f.name); writeStr(out, f.desc);
            }

            // ── Object / array operations ─────────────────────────────────────
            case Opcodes.NEW -> {
                writeOp(out, VmOp.NEW); writeStr(out, ((TypeInsnNode) insn).desc);
            }
            case Opcodes.CHECKCAST -> {
                writeOp(out, VmOp.CHECKCAST); writeStr(out, ((TypeInsnNode) insn).desc);
            }
            case Opcodes.INSTANCEOF -> {
                writeOp(out, VmOp.INSTANCEOF); writeStr(out, ((TypeInsnNode) insn).desc);
            }
            case Opcodes.ARRAYLENGTH  -> writeOp(out, VmOp.ARRAYLENGTH);
            case Opcodes.MONITORENTER -> writeOp(out, VmOp.MONENTER);
            case Opcodes.MONITOREXIT  -> writeOp(out, VmOp.MONEXIT);
            case Opcodes.AALOAD  -> writeOp(out, VmOp.AALOAD);
            case Opcodes.AASTORE -> writeOp(out, VmOp.AASTORE);
            case Opcodes.IALOAD  -> writeOp(out, VmOp.IALOAD);
            case Opcodes.IASTORE -> writeOp(out, VmOp.IASTORE);
            case Opcodes.LALOAD  -> writeOp(out, VmOp.LALOAD);
            case Opcodes.LASTORE -> writeOp(out, VmOp.LASTORE);
            case Opcodes.FALOAD  -> writeOp(out, VmOp.FALOAD);
            case Opcodes.FASTORE -> writeOp(out, VmOp.FASTORE);
            case Opcodes.DALOAD  -> writeOp(out, VmOp.DALOAD);
            case Opcodes.DASTORE -> writeOp(out, VmOp.DASTORE);
            case Opcodes.BALOAD  -> writeOp(out, VmOp.BALOAD);
            case Opcodes.BASTORE -> writeOp(out, VmOp.BASTORE);
            case Opcodes.CALOAD  -> writeOp(out, VmOp.CALOAD);
            case Opcodes.CASTORE -> writeOp(out, VmOp.CASTORE);
            case Opcodes.SALOAD  -> writeOp(out, VmOp.SALOAD);
            case Opcodes.SASTORE -> writeOp(out, VmOp.SASTORE);
            case Opcodes.NEWARRAY -> {
                writeOp(out, VmOp.NEWARRAY);
                out.writeByte(((IntInsnNode) insn).operand);
            }
            case Opcodes.ANEWARRAY -> {
                writeOp(out, VmOp.ANEWARRAY);
                writeStr(out, ((TypeInsnNode) insn).desc);
            }
            case Opcodes.IINC -> {
                // IINC var, increment → LOAD_PRIM var; PUSH_INT inc; IADD; STORE_PRIM var (always an int local)
                IincInsnNode iinc = (IincInsnNode) insn;
                writeOp(out, VmOp.LOAD_PRIM);  out.writeShort(iinc.var);
                writeOp(out, VmOp.PUSH_INT); out.writeInt(iinc.incr);
                writeOp(out, VmOp.IADD);
                writeOp(out, VmOp.STORE_PRIM); out.writeShort(iinc.var);
            }

            default -> throw new UnsupportedOperationException(
                    "Unsupported JVM opcode for virtualization: " + op
                    + " (" + (insn.getClass().getSimpleName()) + ")");
        }
    }

    // ── Category-1/category-2 disambiguation (see class javadoc) ──────────────

    private static VmOp pop2Variant(Frame<BasicValue> frame) {
        return topIsCategory2(frame) ? VmOp.POP2_CAT2 : VmOp.POP2_CAT1;
    }

    private static VmOp dup2Variant(Frame<BasicValue> frame) {
        return topIsCategory2(frame) ? VmOp.DUP2_CAT2 : VmOp.DUP2_CAT1;
    }

    private static VmOp dupX2Variant(Frame<BasicValue> frame) {
        // DUP_X2 form 2 applies when the value just below the top (value2) is category 2;
        // the top value (value1) is always category 1 for a valid DUP_X2.
        int top = frame.getStackSize() - 1;
        BasicValue below = frame.getStack(top - 1);
        return below.getSize() == 2 ? VmOp.DUP_X2_CAT2 : VmOp.DUP_X2_CAT1;
    }

    private static boolean topIsCategory2(Frame<BasicValue> frame) {
        BasicValue top = frame.getStack(frame.getStackSize() - 1);
        return top.getSize() == 2;
    }

    private static void writeOp(DataOutputStream out, VmOp op) throws IOException {
        out.writeByte(op.code);
    }

    private static void emitLdc(LdcInsnNode insn, DataOutputStream out) throws IOException {
        Object cst = insn.cst;
        if (cst instanceof Integer i)  { writeOp(out, VmOp.PUSH_INT);    out.writeInt(i); }
        else if (cst instanceof Long l)  { writeOp(out, VmOp.PUSH_LONG);   out.writeLong(l); }
        else if (cst instanceof Float f) { writeOp(out, VmOp.PUSH_FLOAT);  out.writeInt(Float.floatToIntBits(f)); }
        else if (cst instanceof Double d){ writeOp(out, VmOp.PUSH_DOUBLE); out.writeLong(Double.doubleToLongBits(d)); }
        else if (cst instanceof String s){ writeOp(out, VmOp.PUSH_STRING); writeStr(out, s); }
        else if (cst instanceof Type t)  { writeOp(out, VmOp.PUSH_CLASS);  writeStr(out, t.getInternalName()); }
        else throw new UnsupportedOperationException("Unsupported LDC constant type: " + cst.getClass());
    }

    private static void emitJump(VmOp vmOp, AbstractInsnNode insn, DataOutputStream out,
                                   ByteArrayOutputStream baos, List<Object[]> patches) throws IOException {
        final LabelNode label = ((JumpInsnNode) insn).label;
        writeOp(out, vmOp);
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
