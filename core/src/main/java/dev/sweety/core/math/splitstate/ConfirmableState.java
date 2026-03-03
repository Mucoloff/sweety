package dev.sweety.core.math.splitstate;

import lombok.Getter;

import java.util.function.IntSupplier;

public class ConfirmableState<T> {
    @Getter
    private T value;
    @Getter
    private T oldValue;
    private int lastPreTransaction;

    public ConfirmableState(final T startValue) {
        this.value = startValue;
    }

    public void setValue(final T value) {
        if (value == null) {
            throw new IllegalStateException("Confirmable States do not support null values");
        }

        this.oldValue = this.value;
        this.value = value;
    }

    public void setValueImmediate(final T value) {
        if (value == null) throw new IllegalStateException("Confirmable States do not support null values");

        this.value = value;
    }

    public void setOldValueImmediate(final T value) {
        this.oldValue = value;
    }

    public void setValueCertainly(final T value) {
        if (value == null) throw new IllegalStateException("Confirmable States do not support null values");

        this.value = value;
        this.confirm();
    }

    public void confirm() {
        this.oldValue = null;
    }

    public void checkTransaction(IntSupplier lastTransactionSent, Runnable sendTransaction) {
        final int last = lastTransactionSent.getAsInt();

        if (this.lastPreTransaction == last) sendTransaction.run();

        this.lastPreTransaction = last;
    }
}