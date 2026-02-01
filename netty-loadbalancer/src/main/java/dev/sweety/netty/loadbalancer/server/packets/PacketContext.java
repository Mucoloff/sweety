package dev.sweety.netty.loadbalancer.server.packets;

import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * Contesto di un pacchetto ricevuto, con il sequence ID per mantenere l'ordine.
 * @param packet il pacchetto ricevuto
 * @param ctx il contesto del canale del client
 * @param sequenceId l'ID di sequenza per garantire l'ordine delle risposte
 */
public record PacketContext(Packet packet, ChannelHandlerContext ctx, long sequenceId) {

    /**
     * Costruttore senza sequenceId (per retrocompatibilità, usa -1 come placeholder)
     */
    public PacketContext(Packet packet, ChannelHandlerContext ctx) {
        this(packet, ctx, -1);
    }
}