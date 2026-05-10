package dev.sweety.event.api;

import org.jetbrains.annotations.NotNull;

public class Event implements IEvent {

    private boolean cancelled = false;
    private boolean changed = false;
    private boolean pre = true;

    @Override
    public void cancel() {
        setCancelled(true);
    }

    @Override
    @NotNull
    public <T extends IEvent> T post() {
        this.pre = false;
        //noinspection unchecked
        return (T) this;
    }

    @Override
    public boolean isPost() {
        return !this.pre;
    }

    @Override
    public boolean isPre() {
        return pre;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
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
