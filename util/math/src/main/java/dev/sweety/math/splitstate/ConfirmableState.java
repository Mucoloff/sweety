package dev.sweety.math.splitstate;


import java.util.function.IntSupplier;

public class ConfirmableState<T> {

    private T value, oldValue;
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

    public T value() {
        return value;
    }

    public T oldValue() {
        return oldValue;
    }

    public int lastPreTransaction() {
        return lastPreTransaction;
    }
}