package dev.sweety.core.event;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;


@EqualsAndHashCode
public class Event {

    @Setter
    @Getter
    boolean cancelled = false, changed = false;

    public void cancel() {
        setCancelled(true);
    }

    @Getter
    boolean pre = true;

    public <T extends Event> T post() {
        //noinspection unchecked
        T t = (T) this;
        t.pre = false;
        return t;
    }

    public boolean isPost() {
        return !this.pre;
    }

}