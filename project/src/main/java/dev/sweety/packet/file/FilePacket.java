package dev.sweety.packet.file;

import dev.sweety.event.processor.GenerateEvent;
import dev.sweety.network.cloud.packet.buffer.FileBuffer;
import dev.sweety.network.cloud.packet.model.Packet;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.File;

@GenerateEvent
public class FilePacket extends Packet {

    private FileBuffer _fileBuffer;

    @Getter
    private int size;

    @SneakyThrows
    public FilePacket(File file) {
        FileBuffer.fromFile(file).write(buffer());
    }

    public FilePacket(short _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);

        this.size = buffer().readableBytes();
        this._fileBuffer = FileBuffer.read(buffer());
    }

    public File readFile(File dir) {
        return _fileBuffer.read(dir);
    }

}
