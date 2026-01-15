package dev.sweety.netty.loadbalancer.v2;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.TriFunction;
import dev.sweety.netty.loadbalancer.packets.InternalPacket;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.listener.decoder.PacketDecoder;
import dev.sweety.netty.messaging.listener.encoder.PacketEncoder;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

public abstract class Backend extends Server {

    private final PacketEncoder encoder;
    private final PacketDecoder decoder;

    private final SimpleLogger log = new SimpleLogger(Backend.class);

    public Backend(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(host, port, packetRegistry, packets);
        this.encoder = new PacketEncoder(packetRegistry).noChecksum();
        this.decoder = new PacketDecoder(packetRegistry).noChecksum();
        log.push("internal", AnsiColor.RED_BRIGHT);
    }

    public abstract Packet[] handlePackets(Packet packet);

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (!(packet instanceof InternalPacket internal)) return;
        if (!internal.hasRequest()) return;
        log.push("receive", AnsiColor.WHITE_BRIGHT).info("received internal packet", internal);

        final InternalPacket.Forward request = internal.getRequest();
        log.push("process", AnsiColor.YELLOW_BRIGHT).info("processing forwarded packets", request);

        TriFunction<Packet, Integer, Long, byte[]> constructor = (id, ts, data) -> {
            try {
                return getPacketRegistry().constructPacket(id, ts, data);
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                log.error(e);
                throw new RuntimeException(e);
            }
        };

        Packet[] receivedPackets = request.decode(constructor);

        List<Packet> handledPackets = new java.util.ArrayList<>();
        for (Packet receivedPacket : receivedPackets) {
            Collections.addAll(handledPackets, handlePackets(receivedPacket));
        }

        final InternalPacket.Forward response = new InternalPacket.Forward(getPacketRegistry()::getPacketId, handledPackets.toArray(handledPackets.toArray(Packet[]::new)));

        log.push("forward", AnsiColor.GREEN_BRIGHT).info("sending response");
        sendPacket(ctx, new InternalPacket(internal.getRequestId(), response));

        log.pop();
    }


}
