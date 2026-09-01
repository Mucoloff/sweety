package dev.sweety.transform.vm.core;

/**
 * VM opcode table for the EcstacyTransform lightweight stack machine.
 *
 * Each instruction is 1 byte opcode followed by typed operands.
 * All multi-byte values are big-endian.
 *
 * String/class/member encoding:  [2B short: length][N bytes UTF-8]
 * Jump offsets:                   [4B int: absolute byte offset in bytecode]
 */
public enum VmOp {

    // ── Constants ─────────────────────────────────────────────────────────────
    PUSH_NULL(0x00),
    PUSH_INT(0x01),    // + 4 bytes
    PUSH_LONG(0x02),   // + 8 bytes
    PUSH_FLOAT(0x03),  // + 4 bytes (bits)
    PUSH_DOUBLE(0x04), // + 8 bytes (bits)
    PUSH_STRING(0x05), // + encoded string
    PUSH_CLASS(0x06),  // + encoded class internal name

    // ── Locals ────────────────────────────────────────────────────────────────
    // Split by primitive-vs-reference (known statically at compile time from the real ILOAD/LLOAD/
    // FLOAD/DLOAD vs ALOAD opcode — see VMCompiler) so the interpreter can route to VMLocals' raw
    // `prim` array or `ref` array without any per-op type check, let alone boxing.
    LOAD_PRIM(0x10),  // + 2 bytes (var index)
    STORE_PRIM(0x11), // + 2 bytes (var index)
    LOAD_REF(0x12),   // + 2 bytes (var index)
    STORE_REF(0x13),  // + 2 bytes (var index)

    // ── Integer arithmetic ────────────────────────────────────────────────────
    IADD(0x20), ISUB(0x21), IMUL(0x22), IDIV(0x23), IREM(0x24), INEG(0x25),
    IAND(0x26), IOR(0x27), IXOR(0x28), ISHL(0x29), ISHR(0x2A), IUSHR(0x2B),

    // ── Long arithmetic ───────────────────────────────────────────────────────
    LADD(0x30), LSUB(0x31), LMUL(0x32), LDIV(0x33), LREM(0x34), LNEG(0x35),
    LAND(0x36), LOR(0x37), LXOR(0x38), LCMP(0x39),

    // ── Float arithmetic ──────────────────────────────────────────────────────
    FADD(0x40), FSUB(0x41), FMUL(0x42), FDIV(0x43), FREM(0x44), FNEG(0x45),
    FCMPL(0x46), FCMPG(0x47),

    // ── Double arithmetic ─────────────────────────────────────────────────────
    DADD(0x50), DSUB(0x51), DMUL(0x52), DDIV(0x53), DREM(0x54), DNEG(0x55),
    DCMPL(0x56), DCMPG(0x57),

    // ── Control flow ──────────────────────────────────────────────────────────
    GOTO(0x60),       // + 4 bytes (offset)
    IF_NULL(0x61),    // + 4 bytes (offset)
    IF_NONNULL(0x62), // + 4 bytes (offset)
    IFEQ(0x63), IFNE(0x64), IFLT(0x65), IFGE(0x66), IFGT(0x67), IFLE(0x68), // + 4 bytes (offset) each
    IF_ICMPEQ(0x69), IF_ICMPNE(0x6A), IF_ICMPLT(0x6B), IF_ICMPGE(0x6C),
    IF_ICMPGT(0x6D), IF_ICMPLE(0x6E), // + 4 bytes (offset) each
    IF_ACMPEQ(0x6F), IF_ACMPNE(0x70), // + 4 bytes (offset)

    // ── Return ────────────────────────────────────────────────────────────────
    RETURN(0x80), IRETURN(0x81), LRETURN(0x82), FRETURN(0x83), DRETURN(0x84), ARETURN(0x85),

    // ── Method invocation (resolved via reflection at runtime) ────────────────
    // Operand: [owner][name][desc] — each encoded string
    INVOKE_VIRTUAL(0x90), INVOKE_STATIC(0x91), INVOKE_SPECIAL(0x92), INVOKE_INTERFACE(0x93),

    // ── Field access ──────────────────────────────────────────────────────────
    // Operand: [owner][name][desc] — each encoded string
    GET_FIELD(0xA0), PUT_FIELD(0xA1), GET_STATIC(0xA2), PUT_STATIC(0xA3),

    // ── Stack manipulation ────────────────────────────────────────────────────
    // DUP2/DUP_X2/POP2 are split by JVM stack category (resolved at compile time by VMCompiler
    // via frame analysis) since the VM's operand stack is one boxed slot per JVM value, not the
    // real JVM's category-1/category-2 slot width — the interpreter can't tell which shape applies
    // at runtime, so the compiler bakes the decision into which opcode it emits.
    POP(0xB0),
    POP2_CAT1(0xB1), // two category-1 values (int/float/ref/...)
    DUP(0xB2),
    DUP_X1(0xB3),
    DUP_X2_CAT1(0xB4), // dup below two category-1 values
    DUP2_CAT1(0xB5),   // duplicate top two category-1 values
    SWAP(0xB6),
    POP2_CAT2(0xB7),    // one category-2 value (long/double) — same effect as POP
    DUP_X2_CAT2(0xB8),  // dup below one category-2 value — same effect as DUP_X1
    DUP2_CAT2(0xB9),    // duplicate one category-2 value — same effect as DUP

    // ── Type conversion ───────────────────────────────────────────────────────
    I2L(0xC0), I2F(0xC1), I2D(0xC2), I2B(0xC3), I2C(0xC4), I2S(0xC5),
    L2I(0xC6), L2F(0xC7), L2D(0xC8),
    F2I(0xC9), F2L(0xCA), F2D(0xCB),
    D2I(0xCC), D2L(0xCD), D2F(0xCE),

    // ── Object operations ─────────────────────────────────────────────────────
    NEW(0xD0),         // + encoded class name
    CHECKCAST(0xD1),   // + encoded class name
    INSTANCEOF(0xD2),  // + encoded class name
    ARRAYLENGTH(0xD3),
    THROW(0xD4),
    MONENTER(0xD5),
    MONEXIT(0xD6),

    // ── Array operations ──────────────────────────────────────────────────────
    AALOAD(0xE0), AASTORE(0xE1), IALOAD(0xE2), IASTORE(0xE3),
    LALOAD(0xE4), LASTORE(0xE5), FALOAD(0xE6), FASTORE(0xE7),
    DALOAD(0xE8), DASTORE(0xE9), BALOAD(0xEA), BASTORE(0xEB),
    CALOAD(0xEC), CASTORE(0xED), SALOAD(0xEE), SASTORE(0xEF),
    NEWARRAY(0xF0),  // + 1 byte (JVM type code)
    ANEWARRAY(0xF1); // + encoded class name

    private static final VmOp[] BY_CODE = new VmOp[256];

    static {
        for (VmOp op : values()) {
            int index = op.code & 0xFF;
            if (BY_CODE[index] != null) {
                throw new IllegalStateException("Duplicate VmOp code 0x" + Integer.toHexString(index)
                        + ": " + BY_CODE[index] + " vs " + op);
            }
            BY_CODE[index] = op;
        }
    }

    public final byte code;

    VmOp(int code) {
        this.code = (byte) code;
    }

    /** Decode a raw opcode byte. Throws on an unknown code — never returns null. */
    public static VmOp fromCode(byte raw) {
        VmOp op = BY_CODE[raw & 0xFF];
        if (op == null) {
            throw new IllegalStateException("Unknown VM opcode: 0x" + Integer.toHexString(raw & 0xFF));
        }
        return op;
    }
}
