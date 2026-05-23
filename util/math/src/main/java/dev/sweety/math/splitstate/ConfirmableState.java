package dev.sweety.math.splitstate;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ConfirmableState<T> {

    private T value, oldValue;
    private int lastPreTransaction;

    public ConfirmableState(final T startValue) {
        this.value = startValue;
    }

    public void setValue(@NotNull final T value) {
        Objects.requireNonNull(value, "Confirmable States do not support null values");

        this.oldValue = this.value;
        this.value = value;
    }

    public void setValueImmediate(@NotNull final T value) {
        Objects.requireNonNull(value, "Confirmable States do not support null values");

        this.value = value;
    }

    public void setOldValueImmediate(final T value) {
        this.oldValue = value;
    }

    public void setValueCertainly(@NotNull final T value) {
        Objects.requireNonNull(value, "Confirmable States do not support null values");

        this.value = value;
        this.confirm();
    }

    public void confirm() {
        this.oldValue = null;
    }

    public void checkTransaction(IntSupplier lastTransactionSent, IntConsumer sendTransaction) {
        final int last = lastTransactionSent.getAsInt();

        if (this.lastPreTransaction == last) sendTransaction.accept(last);

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