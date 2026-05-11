package dev.sweety.event.test;

import dev.sweety.event.api.CancellableEvent;
import dev.sweety.event.processor.GenerateEvent;

@GenerateEvent
public interface PlayerJoinEvent extends CancellableEvent<PlayerJoinEvent> {

    String username();
    int getlevel();
}
