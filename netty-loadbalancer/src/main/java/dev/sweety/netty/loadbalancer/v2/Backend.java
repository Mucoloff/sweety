package dev.sweety.netty.loadbalancer.v2;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.TriFunction;
import dev.sweety.netty.loadbalancer.packets.InternalPacket;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

public abstract class Backend extends Server {

    private final SimpleLogger log = new SimpleLogger(Backend.class);

    private final TriFunction<Packet, Integer, Long, byte[]> constructor;

    public Backend(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(host, port, packetRegistry, packets);
        log.push("internal", AnsiColor.RED_BRIGHT);

        constructor = (id, ts, data) -> {
            try {
                return getPacketRegistry().constructPacket(id, ts, data);
            } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                log.error(e);
                throw new RuntimeException(e);
            }
        };
    }

    public abstract Packet[] handlePackets(Packet packet);

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (!(packet instanceof InternalPacket internal)) return;
        if (!internal.hasRequest()) return;
        log.push("receive", AnsiColor.WHITE_BRIGHT).info("received internal packet", internal);

        final InternalPacket.Forward request = internal.getRequest();
        log.push("process", AnsiColor.YELLOW_BRIGHT).info("processing forwarded packets", request);


        final List<Packet> handledPackets = Arrays.stream(request.decode(constructor))
                .flatMap(packets -> Arrays.stream(handlePackets(packets)))
                .collect(Collectors.toList());

        handledPackets.removeIf(Objects::isNull);
        handledPackets.removeIf(p -> p instanceof InternalPacket);

        final Packet[] packets = handledPackets.toArray(handledPackets.toArray(Packet[]::new));

        final InternalPacket.Forward response = new InternalPacket.Forward(getPacketRegistry()::getPacketId, packets);

        log.push("forward", AnsiColor.GREEN_BRIGHT).info("sending response");
        sendPacket(ctx, new InternalPacket(internal.getRequestId(), response));

        log.pop();
    }


}
