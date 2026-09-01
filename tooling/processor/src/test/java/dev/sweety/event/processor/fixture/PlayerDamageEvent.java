package dev.sweety.event.processor.fixture;

import dev.sweety.event.api.CancellableEvent;
import dev.sweety.event.processor.GenerateEvent;
import dev.sweety.processor.Ignore;

@GenerateEvent
public interface PlayerDamageEvent extends CancellableEvent<PlayerDamageEvent> {
    long targetId();
    double damage();
    String source();

    // Default method: must NOT generate a field!
    default boolean isCritical() {
        return damage() > 10.0;
    }

    @Ignore
    default String tag() {
        return "damage";
    }
}
