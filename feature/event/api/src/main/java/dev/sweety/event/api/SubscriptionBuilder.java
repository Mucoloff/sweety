package dev.sweety.event.api;

import dev.sweety.event.api.info.Priority;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.Listener;

public interface SubscriptionBuilder<T extends Event<?>> {
    SubscriptionBuilder<T> priority(int priority);
    
    default SubscriptionBuilder<T> priority(Priority level) {
        return priority(level.getValue());
    }
    
    SubscriptionBuilder<T> state(State state);
    
    default SubscriptionBuilder<T> preOnly() {
        return state(State.PRE);
    }
    
    default SubscriptionBuilder<T> postOnly() {
        return state(State.POST);
    }

    SubscriptionBuilder<T> readOnly(boolean readOnly);
    
    void handle(Listener<T> listener);
}
