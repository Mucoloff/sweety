package dev.sweety.event.api;

import org.jetbrains.annotations.NotNull;

public abstract class AbstractEvent implements MutableEvent {

    private boolean cancelled = false;
    private boolean changed = false;
    private boolean pre = true;

    @Override
    public void cancel() {
        setCancelled(true);
    }

    @Override
    @NotNull
    public Event post() {
        this.pre = false;
        return this;
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
        if (this == o) return true;
        if (!(o instanceof AbstractEvent event)) return false;
        return cancelled == event.cancelled && changed == event.changed && pre == event.pre;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(cancelled, changed, pre);
    }
}
