package dev.sweety.core.event;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode
public class Event implements IEvent {

    @Setter
    @Getter
    boolean cancelled = false;

    @Getter
    boolean changed = false;

    @Override
    public void cancel() {
        setCancelled(true);
    }

    @Getter
    boolean pre = true;

    @Override
    public <T extends Event> T post() {
        //noinspection unchecked
        T t = (T) this;
        t.pre = false;
        return t;
    }

    @Override
    public boolean isPost() {
        return !this.pre;
    }

}