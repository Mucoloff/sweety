package dev.sweety.math.splitstate.common;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Tracks the last pre-confirmation transaction id seen and re-sends it when it stalls
 * (e.g. inventory click transaction the server hasn't acked yet). Deliberately independent
 * of any {@link Prediction}/{@link VersionedValue} — packet ack bookkeeping is a different
 * concern from the value being predicted.
 */
public class TransactionTracker {

    private int lastPreTransaction;

    public void checkTransaction(IntSupplier lastTransactionSent, IntConsumer sendTransaction) {
        final int last = lastTransactionSent.getAsInt();
        if (this.lastPreTransaction == last) sendTransaction.accept(last);
        this.lastPreTransaction = last;
    }

    public int lastPreTransaction() {
        return lastPreTransaction;
    }
}
