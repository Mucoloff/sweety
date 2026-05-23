package dev.sweety.event.impl;

import dev.sweety.event.api.Event;
import dev.sweety.event.api.MutableEvent;
import dev.sweety.event.api.SubscriptionBuilder;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.Listener;

class DefaultSubscriptionBuilder<T extends Event<?>> implements SubscriptionBuilder<T> {

    private final EventSystem system;
    private final Class<T> eventType;
    private int priority = 0;
    private State state = State.BOTH;
    private Boolean readOnlyOverride = null;

    DefaultSubscriptionBuilder(EventSystem system, Class<T> eventType) {
        this.system = system;
        this.eventType = eventType;
    }

    @Override
    public SubscriptionBuilder<T> priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public SubscriptionBuilder<T> state(State state) {
        this.state = state;
        return this;
    }

    @Override
    public SubscriptionBuilder<T> readOnly(boolean readOnly) {
        this.readOnlyOverride = readOnly;
        return this;
    }

    @Override
    public void handle(Listener<T> listener) {
        boolean readOnly = readOnlyOverride != null ? readOnlyOverride : !MutableEvent.class.isAssignableFrom(eventType);
        system.subscribeInternal(eventType, listener, priority, state, readOnly);
    }
}
