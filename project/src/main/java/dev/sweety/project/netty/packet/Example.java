package dev.sweety.project.netty.packet;

import dev.sweety.event.info.State;
import dev.sweety.event.processor.GenerateEvent;
import dev.sweety.packet.processor.BuildPacket;
import dev.sweety.packet.processor.FieldBuffer;
import lombok.Getter;

import java.util.UUID;

@BuildPacket(annotations = {GenerateEvent.class}, name = "ExamplePacketTest", path = "")
public interface Example {

    int value();

    @BuildPacket(annotations = {Getter.class}, name = "flagWrapper")
    boolean flag();

    @BuildPacket(name = "textWrapper")
    String text();

    byte[] testArray();

    Boolean[] testBoolean();

    String[] stringArray();

    UUID uuid();

    UUID[] players();

    State version();

    @FieldBuffer(
            encoder = ExampleEncoder.class,
            decoder = ExampleDecoder.class
    )
    IExampleObj obj();

}
