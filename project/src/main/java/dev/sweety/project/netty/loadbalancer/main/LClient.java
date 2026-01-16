package dev.sweety.project.netty.loadbalancer.main;

import dev.sweety.core.time.StopWatch;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.project.netty.loadbalancer.impl.ClientTest;
import dev.sweety.project.netty.packet.text.TextPacket;
import io.netty.channel.ChannelHandlerContext;

public class LClient {

    public static void main(String[] args) throws Throwable {

        int size = 10;

        final Packet[] packets = new Packet[10];

        for (int i = 0; i < size; i++) {
            packets[i] = new TextPacket("Ciao, questo è un messaggio di prova " + (i + 1));
        }

        final ClientTest client = new ClientTest(LBSettings.LB_HOST, LBSettings.LB_PORT, LBSettings.registry) {

            {
                setOnConnect(c -> {
                            ChannelHandlerContext ctx = c.pipeline().firstContext();
                            Messenger.safeExecute(ctx, ct -> sendPacket(ct, packets));
                        });
            }

        };

        client.start();

        final StopWatch timer = new StopWatch();


        while (true) {
            if (!timer.hasPassedMillis(5000L)) {
                Thread.onSpinWait();
                continue;
            }

            timer.reset();

            //client.sendPacket(new TextPacket("Ping dal client alle " + System.currentTimeMillis()));
        }

    }

}
