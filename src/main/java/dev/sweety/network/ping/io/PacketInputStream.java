package dev.sweety.network.ping.io;

import dev.sweety.network.ping.packet.S2CPacket;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;

public class PacketInputStream {
    InputStream parent;
    boolean autoCompress;
    boolean compressionEnabled;
    Consumer<Boolean> compressionToggle;

    public PacketInputStream(InputStream parent, boolean autoCompress) {
        this(parent, autoCompress, (Consumer) null);
    }

    public PacketInputStream(InputStream parent, boolean autoCompress, Consumer<Boolean> compressionToggle) {
        this(parent, autoCompress, false, compressionToggle);
    }

    public PacketInputStream(InputStream parent, boolean autoCompress, boolean compressionEnabled, Consumer<Boolean> compressionToggle) {
        this.parent = parent;
        this.autoCompress = autoCompress;
        this.compressionEnabled = compressionEnabled;
        this.compressionToggle = compressionToggle;
    }

    private void fireCompressionHandler(boolean b) {
        if (this.compressionToggle != null) {
            this.compressionToggle.accept(b);
        }

    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
        this.fireCompressionHandler(compressionEnabled);
    }

    public void close() throws IOException {
        this.parent.close();
    }

    public S2CPacket read() throws DataFormatException, IOException {
        S2CPacket p = this.compressionEnabled ? S2CPacket.readCompressedFromStream(this.parent) : S2CPacket.readUncompressedFromStream(this.parent);
        if (this.autoCompress && p.getId() == 3) {
            int threshold = p.getPacketReader().readVarInt();
            this.compressionEnabled = threshold > 0;
            this.fireCompressionHandler(this.compressionEnabled);
            p.getPacketReader().reset();
        }

        return p;
    }
}
