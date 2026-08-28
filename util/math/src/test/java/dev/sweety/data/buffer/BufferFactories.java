package dev.sweety.data.buffer;

import org.junit.jupiter.params.provider.Arguments;

import java.util.UUID;
import java.util.stream.Stream;

/** Shared factories and helpers for buffer tests. */
public final class BufferFactories {

    private BufferFactories() {}

    public static Stream<Arguments> allBuffers() {
        return Stream.of(
                Arguments.of("NioBuffer.heap",       (Factory) NioBuffer::heap),
                Arguments.of("NioBuffer.direct",     (Factory) NioBuffer::direct)
                //,Arguments.of("SegmentBuffer.confined",(Factory) SegmentBuffer::confined),
                //Arguments.of("SegmentBuffer.shared",  (Factory) SegmentBuffer::shared),
                //Arguments.of("SegmentBuffer.auto",    (Factory) SegmentBuffer::automatic)
        );
    }

    @FunctionalInterface
    public interface Factory {
        AbstractBuffer<?> create();
    }

    // ===== shared test payload helpers =====

    public static final byte   BYTE_VAL   = 42;
    public static final short  SHORT_VAL  = 1234;
    public static final int    INT_VAL    = 0xCAFEBABE;
    public static final long   LONG_VAL   = 0xDEADBEEFCAFEL;
    public static final float  FLOAT_VAL  = 3.14f;
    public static final double DOUBLE_VAL = 2.718281828;
    public static final char   CHAR_VAL   = '★';
    public static final String STR_VAL    = "héllo wörld";
    public static final UUID   UUID_VAL   = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    public static final boolean[] BOOL_ARRAY = new boolean[]{
            true, false, true, true, false, false, true, false,  // first packed byte
            false, true                                           // second packed byte
    };

    public static final int[]    INT_ARRAY    = {0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 42};
    public static final short[]  SHORT_ARRAY  = {0, 1, -1, Short.MAX_VALUE, Short.MIN_VALUE};
    public static final float[]  FLOAT_ARRAY  = {0f, 1f, -1f, Float.MAX_VALUE, Float.MIN_VALUE, (float) Math.PI};
    public static final double[] DOUBLE_ARRAY = {0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE, Math.E};
    public static final char[]   CHAR_ARRAY   = {'a', 'b', 'Z', '0', '★'};
}
