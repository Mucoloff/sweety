package dev.sweety.packet.processor.fixture;

import dev.sweety.math.pool.Pooled;
import dev.sweety.packet.processor.BuildPacket;
import dev.sweety.processor.Ignore;

@BuildPacket(path = "")
@Pooled
public interface PlayerMovePacketDef {
    double x();
    double y();
    double z();
    float yaw();
    float pitch();
    boolean onGround();

    // Default method: must NOT generate a field!
    default double distanceSquared() {
        return x() * x() + y() * y() + z() * z();
    }

    // Ignored method: must NOT generate a field!
    @Ignore
    default String debugName() {
        return "move";
    }
}
