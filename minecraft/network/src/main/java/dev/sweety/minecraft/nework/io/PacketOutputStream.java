package dev.sweety.minecraft.nework.io;

import dev.sweety.minecraft.nework.packet.C2SPacket;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

public class PacketOutputStream implements Closeable, Flushable {

    private final OutputStream parent;

    private boolean compressionEnabled;

    public PacketOutputStream(OutputStream parent, boolean compress) {
        this.parent = parent;
        this.compressionEnabled = compress;
    }

    public void write(C2SPacket packet) throws IOException {
        final byte[] data = this.compressionEnabled ? packet.toRawCompressed() : packet.toRawUncompressed();
        this.parent.write(data);
    }

    public void close() throws IOException {
        this.parent.close();
    }

    @Override
    public void flush() throws IOException {
        this.parent.flush();
    }

    public boolean compressionEnabled() {
        return compressionEnabled;
    }

    public PacketOutputStream setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
        return this;
    }
}
