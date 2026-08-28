package dev.sweety.transform.vm;

/**
 * Bridge to the client's live session entanglement ({@code SessionKeystream}, in
 * {@code client/src/main/java/dev/luce/bootstrap/session}) without {@code api/feature/transform}
 * compile-depending on the bootstrap module. Bound once at client boot via
 * {@link VMInterpreter#bindSessionFold(SessionFoldSource)}.
 *
 * <p>Unbound (the default, e.g. tests or the server-side compile path that never executes bytecode)
 * means "no session infrastructure exists here" — {@link VMInterpreter} treats that as neutral, the
 * same way {@code SessionKeystream}'s own dev-bypass flag does, not as "session is stale".
 */
public interface SessionFoldSource {

    /** Mirrors {@code SessionKeystream.fresh()} — false once heartbeats have stopped past staleness. */
    boolean fresh();

    /** Mirrors {@code SessionKeystream.fold(long)} — deterministic entangled value for the given salt. */
    long fold(long salt);
}
