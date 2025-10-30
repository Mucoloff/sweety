package dev.sweety.network.cloud.impl.file;

import dev.sweety.network.cloud.impl.PacketRegistry;
import dev.sweety.network.cloud.packet.buffer.FileBuffer;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import dev.sweety.network.cloud.packet.model.IPacket;
import dev.sweety.network.cloud.packet.outgoing.PacketOut;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.File;

public interface FilePacket extends IPacket {

    class In extends PacketIn implements FilePacket {

        private final FileBuffer fileBuffer;
        @Getter
        private final int size;

        public In(PacketIn packet) {
            super(packet);
            this.size = buffer.readableBytes();
            this.fileBuffer = FileBuffer.read(buffer);
        }

        public File readFile(File dir) {
            return fileBuffer.read(dir);
        }

    }

    class Out extends PacketOut implements FilePacket {

        @SneakyThrows
        public Out(final File file) {
            super(PacketRegistry.FILE.id());
            FileBuffer.fromFile(file).write(buffer);
        }
    }
}
