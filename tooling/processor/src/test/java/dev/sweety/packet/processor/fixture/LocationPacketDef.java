package dev.sweety.packet.processor.fixture;

import dev.sweety.packet.processor.BuildPacket;

@BuildPacket(path = "")
public interface LocationPacketDef {
    String name();
    Point position();
}
