package dev.sweety.data.buffer;

import dev.sweety.exception.PacketDecodeException;
import java.io.EOFException;

/**
 * Trait interface that abstracts packed-boolean logic.
 * The implementing buffer must hold the 5 primitive state fields and provide accessors.
 */
public interface PackedBooleanAccessor<Self extends BufferReader & BufferWriter> {

    byte writeMask();
    void writeMask(byte writeMask);

    byte writeMaskIndex();
    void writeMaskIndex(byte writeMaskIndex);

    int writePosIndex();
    void writePosIndex(int writePosIndex);

    byte readMask();
    void readMask(byte readMask);

    byte readMaskIndex();
    void readMaskIndex(byte readMaskIndex);

    /**
     * Resets packed-boolean read state. Call whenever the read cursor is moved without matching how many
     * booleans were consumed (for example rereaderIndex or readerIndex(int)).
     */
    default void resetPackedBooleanReadState() {
        readMask((byte) 0);
        readMaskIndex((byte) 0);
    }

    /**
     * Resets packed-boolean write state. Call when the write cursor is rewound (for example
     * rewriterIndex or writerIndex(int)).
     */
    default void resetPackedBooleanWriteState() {
        writeMask((byte) 0);
        writeMaskIndex((byte) 0);
        writePosIndex(0);
    }


    default Self writeBoolean(boolean value) {
        //noinspection unchecked
        Self self = (Self) this;
        byte writeMaskIndex = writeMaskIndex();
        byte writeMask = writeMask();

        if (writeMaskIndex % 8 == 0) {
            writePosIndex(self.writerIndex());
            writeMask = 0;
            self.writeByte((byte) 0);
        }

        if (value) writeMask |= (byte) (1 << (writeMaskIndex % 8));

        self.setByte(writePosIndex(), writeMask);
        writeMask(writeMask);
        writeMaskIndex((byte) (writeMaskIndex + 1));
        return self;
    }

    default boolean readBoolean() {
        //noinspection unchecked
        Self self = (Self) this;
        byte readMaskIndex = readMaskIndex();
        byte readMask = readMask();

        if (readMaskIndex % 8 == 0) {
            if (!self.isReadable()) {
                throw new PacketDecodeException("Unable to read boolean", new EOFException()).runtime();
            }
            readMask = self.readByte();
            readMask(readMask);
        }

        boolean result = ((readMask >> (readMaskIndex % 8)) & 1) != 0;
        readMaskIndex((byte) (readMaskIndex + 1));
        return result;
    }
}
