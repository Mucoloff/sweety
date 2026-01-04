package dev.sweety.project.netty.packet;

import dev.sweety.event.info.State;
import dev.sweety.packet.processor.BuildPacket;

import java.util.UUID;

@BuildPacket
public interface Example {

    int value();

    boolean flag();

    String text();

    byte[] testArray();

    Boolean[] testBoolean();

    String[] stringArray();

    UUID uuid();

    UUID[] players();

    State version();

}
