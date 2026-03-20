package dev.sweety.event.api;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode
public class Event implements IEvent {

    @Setter
    @Getter
    boolean cancelled = false;

    @Getter
    volatile boolean changed = false;

    @Override
    public void cancel() {
        setCancelled(true);
    }

    @Getter
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

}
