package dev.sweety.minecraft.nework.io;

import dev.sweety.minecraft.nework.packet.S2CPacket;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;

public class PacketInputStream implements Closeable {
    private final InputStream parent;
    private final boolean autoCompress;
    private boolean compressionEnabled;
    private final Consumer<Boolean> compressionToggle;

    public PacketInputStream(InputStream parent, boolean autoCompress) {
        this(parent, autoCompress, null);
    }

    public PacketInputStream(InputStream parent, boolean autoCompress, Consumer<Boolean> compressionToggle) {
        this(parent, autoCompress, false, compressionToggle);
    }

    public PacketInputStream(InputStream parent, boolean autoCompress, boolean compressionEnabled, Consumer<Boolean> compressionToggle) {
        this.parent = parent instanceof BufferedInputStream ? parent : new BufferedInputStream(parent);
        this.autoCompress = autoCompress;
        this.compressionEnabled = compressionEnabled;
        this.compressionToggle = compressionToggle;
    }

    private void fireCompressionHandler(boolean compressionEnabled) {
        if (this.compressionToggle != null) {
            this.compressionToggle.accept(compressionEnabled);
        }
    }

    public void setCompressionEnabled(boolean compressionEnabled) {
        this.compressionEnabled = compressionEnabled;
        this.fireCompressionHandler(compressionEnabled);
    }

    @Override
    public void close() throws IOException {
        this.parent.close();
    }

    public S2CPacket read() throws DataFormatException, IOException {
        S2CPacket p = this.compressionEnabled ? S2CPacket.readCompressedFromStream(this.parent) : S2CPacket.readUncompressedFromStream(this.parent);

        if (this.autoCompress && p.id() == 3) {
            int threshold = p.packetReader().readVarInt();
            this.compressionEnabled = threshold > 0;
            this.fireCompressionHandler(this.compressionEnabled);
            p.packetReader().reset();
        }

        return p;
    }
}
