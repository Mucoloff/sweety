package dev.sweety.event.api;

public abstract class AbstractCancellableEvent<E extends CancellableEvent<E>> extends AbstractEvent<E> implements CancellableEvent<E> {

    protected boolean cancelled = false;

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void cancel() {
        this.cancelled = true;
    }

    @Override
    public void uncancel() {
        this.cancelled = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractCancellableEvent<?> that)) return false;
        if (!super.equals(o)) return false;
        return cancelled == that.cancelled;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), cancelled);
    }
}
