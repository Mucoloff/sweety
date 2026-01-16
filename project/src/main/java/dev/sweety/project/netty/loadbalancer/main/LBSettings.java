package dev.sweety.project.netty.loadbalancer.main;

import dev.sweety.netty.loadbalancer.common.packet.InternalPacket;
import dev.sweety.netty.messaging.exception.PacketRegistrationException;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.project.netty.packet.text.TextPacket;
import lombok.experimental.UtilityClass;

@UtilityClass
public class LBSettings {

    public final String LB_HOST, BK1_HOST, BK2_HOST;
    public final int LB_PORT, BK1_PORT, BK2_PORT;

    public final IPacketRegistry registry;

    static {
        LB_HOST = BK1_HOST = BK2_HOST = "127.0.0.1";
        LB_PORT = 30000;
        BK1_PORT = 30001;
        BK2_PORT = 30002;
        try {
            registry = new OptimizedPacketRegistry(TextPacket.class, InternalPacket.class);
        } catch (PacketRegistrationException e) {
            throw new RuntimeException(e);
        }
    }


}
