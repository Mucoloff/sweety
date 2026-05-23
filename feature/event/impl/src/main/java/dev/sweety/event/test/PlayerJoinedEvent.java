package dev.sweety.event.test;

import dev.sweety.event.api.CancellableEvent;
import dev.sweety.event.processor.GenerateEvent;

@GenerateEvent
public interface PlayerJoinedEvent extends CancellableEvent<PlayerJoinedEvent> {

    String username();
    int getlevel();
}
