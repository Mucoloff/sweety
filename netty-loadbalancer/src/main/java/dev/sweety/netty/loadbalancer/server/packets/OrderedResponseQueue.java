package dev.sweety.netty.loadbalancer.server.packets;

import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Gestisce l'ordine delle risposte per un singolo client.
 * Garantisce che le risposte vengano inviate nello stesso ordine in cui sono stati ricevuti i pacchetti.
 */
public class OrderedResponseQueue {

    private final ChannelHandlerContext ctx;
    private final BiConsumer<ChannelHandlerContext, Packet[]> sender;

    // Sequence number per i pacchetti in arrivo
    private final AtomicLong nextSequence = new AtomicLong(0);
    // Prossimo sequence number da inviare (protetto da sendLock)
    private long nextToSend = 0;

    // Buffer per le risposte fuori ordine: sequenceId -> risposta
    private final Map<Long, Packet[]> pendingResponses = new ConcurrentHashMap<>();

    // Lock per sincronizzare l'invio ordinato
    private final Object sendLock = new Object();

    public OrderedResponseQueue(ChannelHandlerContext ctx, BiConsumer<ChannelHandlerContext, Packet[]> sender) {
        this.ctx = ctx;
        this.sender = sender;
    }

    /**
     * Ottiene il prossimo sequence number per un nuovo pacchetto in arrivo.
     * @return il sequence number assegnato
     */
    public long nextSequenceId() {
        return nextSequence.getAndIncrement();
    }

    /**
     * Completa una richiesta con la risposta.
     * La risposta verrà inviata immediatamente se è il prossimo in ordine,
     * altrimenti verrà bufferizzata fino a quando non sarà il suo turno.
     *
     * @param sequenceId il sequence number della richiesta originale
     * @param responses i pacchetti di risposta
     */
    public void complete(long sequenceId, Packet[] responses) {
        synchronized (sendLock) {
            if (sequenceId == nextToSend) {
                // È il prossimo da inviare, invia subito
                sendResponse(responses);
                nextToSend++;

                // Controlla se ci sono altre risposte in coda pronte per essere inviate
                drainReady();
            } else {
                // Non è ancora il suo turno, bufferizza
                pendingResponses.put(sequenceId, responses);
            }
        }
    }

    /**
     * Invia tutte le risposte consecutive pronte nel buffer.
     */
    private void drainReady() {
        Packet[] next;
        while ((next = pendingResponses.remove(nextToSend)) != null) {
            sendResponse(next);
            nextToSend++;
        }
    }

    private void sendResponse(Packet[] responses) {
        if (responses == null || responses.length == 0) return;
        if (!ctx.channel().isActive()) return;

        Messenger.safeExecute(ctx, c -> sender.accept(c, responses));
    }

    /**
     * Resetta la coda (da chiamare quando il client si disconnette).
     */
    public void reset() {
        synchronized (sendLock) {
            nextSequence.set(0);
            nextToSend = 0;
            pendingResponses.clear();
        }
    }

    /**
     * @return il numero di risposte in attesa di essere inviate
     */
    public int pendingCount() {
        return pendingResponses.size();
    }
}
