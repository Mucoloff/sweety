package dev.sweety.netty.loadbalancer.v2;

import dev.sweety.netty.loadbalancer.packets.InternalPacket;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.listener.decoder.PacketDecoder;
import dev.sweety.netty.messaging.listener.encoder.PacketEncoder;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;

public abstract class Backend extends Server {

    private final PacketEncoder encoder;
    private final PacketDecoder decoder;

    public Backend(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(host, port, packetRegistry, packets);
        this.encoder = new PacketEncoder(packetRegistry).noChecksum();
        this.decoder = new PacketDecoder(packetRegistry).noChecksum();
    }

    public abstract Packet[] handlePackets(Packet[] packets);

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (!(packet instanceof InternalPacket internal)) return;
        if (!internal.hasRequest()) return;

        final Packet[] requests = internal.getRequest().decode(decoder);
        final Packet[] responses = handlePackets(requests);
        final InternalPacket.Forward forward = new InternalPacket.Forward(encoder, responses);

        sendPacket(ctx, new InternalPacket(internal.getRequestId(), forward));
    }


}
