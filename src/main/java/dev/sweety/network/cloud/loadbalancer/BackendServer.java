package dev.sweety.network.cloud.loadbalancer;

import dev.sweety.network.cloud.messaging.Server;
import dev.sweety.network.cloud.packet.incoming.PacketIn;
import dev.sweety.network.cloud.packet.outgoing.PacketOut;
import io.netty.channel.ChannelHandlerContext;

public abstract class BackendServer extends Server {
    public BackendServer(String host, int port, PacketOut... packets) {
        super(host, port, packets);
    }


    /**
     * Metodo astratto che le implementazioni di backend devono definire.
     * Riceve il pacchetto dal client e deve restituire un array di pacchetti di risposta.
     * La gestione del correlationId è completamente trasparente.
     *
     * @param ctx    Il contesto del canale.
     * @param packet Il pacchetto in arrivo.
     * @return Un array di PacketOut da inviare come risposta.
     */
    public abstract PacketOut[] handlePackets(ChannelHandlerContext ctx, PacketIn packet);

    public static final byte CLOSING_ID = (byte) 0xFF;

    @Override
    public final void onPacketReceive(ChannelHandlerContext ctx, PacketIn packet) {
        if (!ctx.channel().isActive()) return;

        // 1. Leggi il correlationId, che è sempre all'inizio.
        long correlationId = packet.getBuffer().readLong();

        // 2. Crea un nuovo pacchetto "pulito" con solo i dati originali per la logica di business.
        PacketIn originalClientPacket = new PacketIn(packet.getId(), packet.getTimestamp(), packet.getBuffer().readByteArray());

        // 3. Chiama la logica di business dell'utente.
        PacketOut[] responses = handlePackets(ctx, originalClientPacket);

        if (responses != null && responses.length != 0) {// 4. Invia le risposte wrappate.
            for (PacketOut response : responses) {
                response.getBuffer().insertAtStart(buffer -> {
                    buffer.writeLong(correlationId);
                });

                /*
                PacketOut finalResponse = new PacketOut(response.getId(), response.getTimestamp(), correlationId);
                finalResponse.getBuffer().writeBytes(response.getData());
                responses[i] = finalResponse;
                 */
            }

            send(ctx, responses);
        }

        PacketOut closingPacket = new PacketOut(CLOSING_ID);
        closingPacket.getBuffer().writeLong(correlationId);
        send(ctx, closingPacket);
    }


}
