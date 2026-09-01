package dev.sweety.saas.hub;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding active {@link DualTransportSession} instances keyed by 64-bit session ID.
 */
public final class SessionRegistry {

    private final ConcurrentHashMap<Long, DualTransportSession> sessions = new ConcurrentHashMap<>();

    public void register(DualTransportSession session) {
        sessions.put(session.sessionId(), session);
    }

    public DualTransportSession get(long sessionId) {
        return sessions.get(sessionId);
    }

    public DualTransportSession remove(long sessionId) {
        return sessions.remove(sessionId);
    }

    public Collection<DualTransportSession> all() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public int activeCount() {
        return sessions.size();
    }
}
