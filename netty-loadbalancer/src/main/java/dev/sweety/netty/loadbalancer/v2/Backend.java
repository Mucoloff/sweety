package dev.sweety.netty.loadbalancer.v2;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.math.function.TriFunction;
import dev.sweety.netty.loadbalancer.packets.InternalPacket;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class Backend extends Server {

    private final SimpleLogger _logger = new SimpleLogger(Backend.class);
    private final TriFunction<Packet, Integer, Long, byte[]> constructor;

    public Backend(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(host, port, packetRegistry, packets);
        this._logger.push("internal", AnsiColor.RED_BRIGHT);
        this.constructor = (id, ts, data) -> packetRegistry.construct(id, ts, data, this._logger);
    }

    public abstract Packet[] handlePackets(Packet packet);

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (!(packet instanceof InternalPacket internal) || !internal.hasRequest()) return;
        final InternalPacket.Forward request = internal.getRequest();

        this._logger.push("receive", AnsiColor.WHITE_BRIGHT).info("received internal packet", internal);
        this._logger.push("process", AnsiColor.YELLOW_BRIGHT).info("processing forwarded packets", request);

        final List<Packet> handledPackets = Arrays.stream(request.decode(this.constructor))
                .flatMap(packets -> Arrays.stream(handlePackets(packets)))
                .collect(Collectors.toList());

        handledPackets.removeIf(Objects::isNull);
        handledPackets.removeIf(p -> p instanceof InternalPacket);
        final Packet[] packets = handledPackets.toArray(handledPackets.toArray(Packet[]::new));
        final InternalPacket.Forward response = new InternalPacket.Forward(getPacketRegistry()::getPacketId, packets);

        this._logger.push("forward", AnsiColor.GREEN_BRIGHT).info("sending response");
        sendPacket(ctx, new InternalPacket(internal.getRequestId(), response));
        this._logger.pop();
    }

    public SimpleLogger _logger() {
        return _logger;
    }
}
