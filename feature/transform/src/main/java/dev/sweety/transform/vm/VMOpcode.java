package dev.sweety.transform.vm;

/**
 * VM opcode table for the EcstacyTransform lightweight stack machine.
 *
 * Each instruction is 1 byte opcode followed by typed operands.
 * All multi-byte values are big-endian.
 *
 * String/class/member encoding:  [2B short: length][N bytes UTF-8]
 * Jump offsets:                   [4B int: absolute byte offset in bytecode]
 */
public final class VMOpcode {

    private VMOpcode() {}

    // ── Constants ─────────────────────────────────────────────────────────────
    public static final byte PUSH_NULL   = 0x00;
    public static final byte PUSH_INT    = 0x01; // + 4 bytes
    public static final byte PUSH_LONG   = 0x02; // + 8 bytes
    public static final byte PUSH_FLOAT  = 0x03; // + 4 bytes (bits)
    public static final byte PUSH_DOUBLE = 0x04; // + 8 bytes (bits)
    public static final byte PUSH_STRING = 0x05; // + encoded string
    public static final byte PUSH_CLASS  = 0x06; // + encoded class internal name

    // ── Locals ────────────────────────────────────────────────────────────────
    public static final byte LOAD  = 0x10; // + 2 bytes (var index)
    public static final byte STORE = 0x11; // + 2 bytes (var index)

    // ── Integer arithmetic ────────────────────────────────────────────────────
    public static final byte IADD  = 0x20;
    public static final byte ISUB  = 0x21;
    public static final byte IMUL  = 0x22;
    public static final byte IDIV  = 0x23;
    public static final byte IREM  = 0x24;
    public static final byte INEG  = 0x25;
    public static final byte IAND  = 0x26;
    public static final byte IOR   = 0x27;
    public static final byte IXOR  = 0x28;
    public static final byte ISHL  = 0x29;
    public static final byte ISHR  = 0x2A;
    public static final byte IUSHR = 0x2B;

    // ── Long arithmetic ───────────────────────────────────────────────────────
    public static final byte LADD  = 0x30;
    public static final byte LSUB  = 0x31;
    public static final byte LMUL  = 0x32;
    public static final byte LDIV  = 0x33;
    public static final byte LREM  = 0x34;
    public static final byte LNEG  = 0x35;
    public static final byte LAND  = 0x36;
    public static final byte LOR   = 0x37;
    public static final byte LXOR  = 0x38;
    public static final byte LCMP  = 0x39;

    // ── Float arithmetic ──────────────────────────────────────────────────────
    public static final byte FADD  = 0x40;
    public static final byte FSUB  = 0x41;
    public static final byte FMUL  = 0x42;
    public static final byte FDIV  = 0x43;
    public static final byte FREM  = 0x44;
    public static final byte FNEG  = 0x45;
    public static final byte FCMPL = 0x46;
    public static final byte FCMPG = 0x47;

    // ── Double arithmetic ─────────────────────────────────────────────────────
    public static final byte DADD  = 0x50;
    public static final byte DSUB  = 0x51;
    public static final byte DMUL  = 0x52;
    public static final byte DDIV  = 0x53;
    public static final byte DREM  = 0x54;
    public static final byte DNEG  = 0x55;
    public static final byte DCMPL = 0x56;
    public static final byte DCMPG = 0x57;

    // ── Control flow ──────────────────────────────────────────────────────────
    public static final byte GOTO       = 0x60; // + 4 bytes (offset)
    public static final byte IF_NULL    = 0x61; // + 4 bytes (offset)
    public static final byte IF_NONNULL = 0x62; // + 4 bytes (offset)
    public static final byte IFEQ       = 0x63; // + 4 bytes (offset)
    public static final byte IFNE       = 0x64;
    public static final byte IFLT       = 0x65;
    public static final byte IFGE       = 0x66;
    public static final byte IFGT       = 0x67;
    public static final byte IFLE       = 0x68;
    public static final byte IF_ICMPEQ  = 0x69; // + 4 bytes (offset)
    public static final byte IF_ICMPNE  = 0x6A;
    public static final byte IF_ICMPLT  = 0x6B;
    public static final byte IF_ICMPGE  = 0x6C;
    public static final byte IF_ICMPGT  = 0x6D;
    public static final byte IF_ICMPLE  = 0x6E;
    public static final byte IF_ACMPEQ  = 0x6F;
    public static final byte IF_ACMPNE  = 0x70;

    // ── Return ────────────────────────────────────────────────────────────────
    public static final byte RETURN  = (byte) 0x80;
    public static final byte IRETURN = (byte) 0x81;
    public static final byte LRETURN = (byte) 0x82;
    public static final byte FRETURN = (byte) 0x83;
    public static final byte DRETURN = (byte) 0x84;
    public static final byte ARETURN = (byte) 0x85;

    // ── Method invocation (resolved via reflection at runtime) ────────────────
    // Operand: [owner][name][desc] — each encoded string
    public static final byte INVOKE_VIRTUAL   = (byte) 0x90;
    public static final byte INVOKE_STATIC    = (byte) 0x91;
    public static final byte INVOKE_SPECIAL   = (byte) 0x92;
    public static final byte INVOKE_INTERFACE = (byte) 0x93;

    // ── Field access ──────────────────────────────────────────────────────────
    // Operand: [owner][name][desc] — each encoded string
    public static final byte GET_FIELD   = (byte) 0xA0;
    public static final byte PUT_FIELD   = (byte) 0xA1;
    public static final byte GET_STATIC  = (byte) 0xA2;
    public static final byte PUT_STATIC  = (byte) 0xA3;

    // ── Stack manipulation ────────────────────────────────────────────────────
    public static final byte POP    = (byte) 0xB0;
    public static final byte POP2   = (byte) 0xB1;
    public static final byte DUP    = (byte) 0xB2;
    public static final byte DUP_X1 = (byte) 0xB3;
    public static final byte DUP_X2 = (byte) 0xB4;
    public static final byte DUP2   = (byte) 0xB5;
    public static final byte SWAP   = (byte) 0xB6;

    // ── Type conversion ───────────────────────────────────────────────────────
    public static final byte I2L = (byte) 0xC0;
    public static final byte I2F = (byte) 0xC1;
    public static final byte I2D = (byte) 0xC2;
    public static final byte I2B = (byte) 0xC3;
    public static final byte I2C = (byte) 0xC4;
    public static final byte I2S = (byte) 0xC5;
    public static final byte L2I = (byte) 0xC6;
    public static final byte L2F = (byte) 0xC7;
    public static final byte L2D = (byte) 0xC8;
    public static final byte F2I = (byte) 0xC9;
    public static final byte F2L = (byte) 0xCA;
    public static final byte F2D = (byte) 0xCB;
    public static final byte D2I = (byte) 0xCC;
    public static final byte D2L = (byte) 0xCD;
    public static final byte D2F = (byte) 0xCE;

    // ── Object operations ─────────────────────────────────────────────────────
    public static final byte NEW         = (byte) 0xD0; // + encoded class name
    public static final byte CHECKCAST   = (byte) 0xD1; // + encoded class name
    public static final byte INSTANCEOF  = (byte) 0xD2; // + encoded class name
    public static final byte ARRAYLENGTH = (byte) 0xD3;
    public static final byte THROW       = (byte) 0xD4;
    public static final byte MONENTER    = (byte) 0xD5;
    public static final byte MONEXIT     = (byte) 0xD6;

    // ── Array operations ──────────────────────────────────────────────────────
    public static final byte AALOAD  = (byte) 0xE0;
    public static final byte AASTORE = (byte) 0xE1;
    public static final byte IALOAD  = (byte) 0xE2;
    public static final byte IASTORE = (byte) 0xE3;
    public static final byte LALOAD  = (byte) 0xE4;
    public static final byte LASTORE = (byte) 0xE5;
    public static final byte FALOAD  = (byte) 0xE6;
    public static final byte FASTORE = (byte) 0xE7;
    public static final byte DALOAD  = (byte) 0xE8;
    public static final byte DASTORE = (byte) 0xE9;
    public static final byte BALOAD  = (byte) 0xEA;
    public static final byte BASTORE = (byte) 0xEB;
    public static final byte CALOAD  = (byte) 0xEC;
    public static final byte CASTORE = (byte) 0xED;
    public static final byte SALOAD  = (byte) 0xEE;
    public static final byte SASTORE = (byte) 0xEF;
    public static final byte NEWARRAY  = (byte) 0xF0; // + 1 byte (JVM type code)
    public static final byte ANEWARRAY = (byte) 0xF1; // + encoded class name
}
