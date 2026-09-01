package dev.sweety.netty.packet.queue;

import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.model.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;

/**
 * Gestisce l'ordine delle risposte per un singolo client.
 * Garantisce che le risposte vengano inviate nello stesso ordine in cui sono stati ricevuti i pacchetti.
 */
public class OrderedResponseQueue {

    private final ChannelHandlerContext ctx;
    private final BiConsumer<ChannelHandlerContext, Packet[]> sender;

    private final LongAdder nextSequence = new LongAdder();
    private long nextToSend = 0;

    // Buffer per le risposte fuori ordine: sequenceId -> risposta
    private final Map<Long, Packet[]> pendingResponses = new ConcurrentHashMap<>();

    private final Object sendLock = new Object();

    public OrderedResponseQueue(ChannelHandlerContext ctx, BiConsumer<ChannelHandlerContext, Packet[]> sender) {
        this.ctx = ctx;
        this.sender = sender;
    }

    /**
     * Ottiene il prossimo sequence number per un nuovo pacchetto in arrivo.
     *
     * @return il sequence number assegnato
     */
    public long nextSequenceId() {
        final long val = nextSequence.sum();
        nextSequence.increment();
        return val;
    }

    /**
     * Completa una richiesta con la risposta.
     * La risposta verrà inviata immediatamente se è il prossimo in ordine,
     * altrimenti verrà bufferizzata fino a quando non sarà il suo turno.
     *
     * @param sequenceId il sequence number della richiesta originale
     * @param responses  i pacchetti di risposta
     */
    public void complete(long sequenceId, Packet[] responses) {
        synchronized (sendLock) {
            if (sequenceId != nextToSend) {
                pendingResponses.put(sequenceId, responses);
                //re-enqueue
                return;
            }
            sendResponse(responses);
            nextToSend++;
            drainReady();
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
        Messenger.safeRun(ctx, c -> sender.accept(c, responses));
    }

    /**
     * Resetta la coda (da chiamare quando il client si disconnette).
     */
    public void reset() {
        synchronized (sendLock) {
            nextSequence.reset();
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