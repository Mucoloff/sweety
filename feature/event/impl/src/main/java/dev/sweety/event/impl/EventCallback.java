package dev.sweety.event.impl;

import dev.sweety.event.api.Event;
import dev.sweety.event.api.info.State;
import dev.sweety.event.api.listener.Listener;

record EventCallback<T extends Event<?>>(
        Object container,
        Listener<T> listener,
        int priority,
        State state,
        boolean readOnly,
        boolean parallel,
        long subscribeOrder) {
}
