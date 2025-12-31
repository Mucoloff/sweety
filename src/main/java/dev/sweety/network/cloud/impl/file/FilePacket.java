package dev.sweety.network.cloud.impl.file;

import dev.sweety.network.cloud.packet.buffer.FileBuffer;
import dev.sweety.network.cloud.packet.model.Packet;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.File;

public class FilePacket extends Packet {

    private FileBuffer fileBuffer;

    @Getter
    private int size;

    @SneakyThrows
    public FilePacket(File file) {
        FileBuffer.fromFile(file).write(buffer());
    }

    public FilePacket(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);

        this.size = buffer().readableBytes();
        this.fileBuffer = FileBuffer.read(buffer());
    }

    public File readFile(File dir) {
        return fileBuffer.read(dir);
    }

}
