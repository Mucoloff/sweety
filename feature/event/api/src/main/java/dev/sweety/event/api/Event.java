package dev.sweety.event.api;

public class Event implements IEvent {

    boolean cancelled = false;

    volatile boolean changed = false;

    @Override
    public void cancel() {
        setCancelled(true);
    }

    boolean pre = true;

    @Override
    public <T extends IEvent> T post() {
        this.pre = false;
        //noinspection unchecked
        return (T) this;
    }

    @Override
    public boolean isPost() {
        return !this.pre;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isChanged() {
        return changed;
    }

    public boolean isPre() {
        return pre;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Event event)) return false;

        return isCancelled() == event.isCancelled() && isChanged() == event.isChanged() && isPre() == event.isPre();
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(isCancelled());
        result = 31 * result + Boolean.hashCode(isChanged());
        result = 31 * result + Boolean.hashCode(isPre());
        return result;
    }
}
