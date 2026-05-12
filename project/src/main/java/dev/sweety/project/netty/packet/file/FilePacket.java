package dev.sweety.project.netty.packet.file;

import dev.sweety.event.processor.GenerateEvent;
import dev.sweety.netty.packet.buffer.FileBuffer;
import dev.sweety.netty.packet.model.Packet;
import lombok.Getter;
import lombok.SneakyThrows;

import java.nio.file.Path;

@GenerateEvent
public class FilePacket extends Packet {

    private FileBuffer _fileBuffer;

    @Getter
    private int size;

    @SneakyThrows
    public FilePacket(Path file) {
        FileBuffer.fromFile(file).write(buffer());
    }

    public FilePacket(final int _id,final long _timestamp,final byte[] _data) {
        super(_id, _timestamp, _data);
        this.size = buffer().readableBytes();
        this._fileBuffer = FileBuffer.read(buffer());
    }

    public Path readFile(Path dir) {
        return _fileBuffer.read(dir);
    }

}
