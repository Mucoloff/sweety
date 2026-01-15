package dev.sweety.project.netty.lb.main;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.project.netty.lb.impl.ClientTest;
import dev.sweety.project.netty.packet.text.TextPacket;

public class LClient {

    public static void main(String[] args) throws Throwable {

        final ClientTest client = new ClientTest(LBSettings.LB_HOST, LBSettings.LB_PORT, LBSettings.registry);

        client.start();

        Thread.sleep(2000L);

        Packet[] packets = new Packet[]{
                new TextPacket("Ciao, questo è un messaggio di prova 1"),
                /*
                new TextPacket("Ciao, questo è un messaggio di prova 2"),
                new TextPacket("Ciao, questo è un messaggio di prova 3"),
                new TextPacket("Ciao, questo è un messaggio di prova 4"),
                new TextPacket("Ciao, questo è un messaggio di prova 5"),
                 */
        };

        client.sendPacket(packets);


        while (true) {
            Thread.onSpinWait();
        }

    }

}
