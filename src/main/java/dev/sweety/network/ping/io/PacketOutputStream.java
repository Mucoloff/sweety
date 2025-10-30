package dev.sweety.network.ping.io;

import dev.sweety.network.ping.packet.C2SPacket;

import java.io.IOException;
import java.io.OutputStream;

public class PacketOutputStream {
    OutputStream parent;
    boolean compressionEnabled;

    public PacketOutputStream(OutputStream parent, boolean compress) {
        this.parent = parent;
        this.compressionEnabled = compress;
    }

    public boolean isCompressionEnabled() {
        return this.compressionEnabled;
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
    }

    public void write(C2SPacket p) throws IOException {
        byte[] c = this.compressionEnabled ? p.toRawCompressed() : p.toRawUncompressed();
        this.parent.write(c);
    }

    public void close() throws IOException {
        this.parent.close();
    }
}
