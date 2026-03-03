package dev.sweety.project.netty.loadbalancer.impl;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.netty.loadbalancer.backend.Backend;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.project.netty.packet.text.TextPacket;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;

public class BackendTest extends Backend {

    private final SimpleLogger logger = new SimpleLogger(BackendTest.class);

    public BackendTest(String host, int port, IPacketRegistry packetRegistry, Packet... packets) {
        super(host, port, packetRegistry);

        onConnect(c -> Messenger.safeRun(c.pipeline().firstContext(), ctx -> sendPacket(packets)));
    }

    @Override
    public void handleInternal(Packet packet, ArrayList<Packet> results) {
        String text = (packet instanceof TextPacket t) ? t.getText() : "Unknown Packet Type";
        logger.info("Contenuto:", text);

        results.add(new TextPacket(port + "/" + text));
    }

    @Override
    public void leave(ChannelHandlerContext ctx) {

    }

    @Override
    public int typeId() {
        return 0;
    }
}
