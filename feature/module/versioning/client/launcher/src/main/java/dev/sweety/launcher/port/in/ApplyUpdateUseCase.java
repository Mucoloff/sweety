package dev.sweety.launcher.port.in;

import dev.sweety.launcher.domain.update.UpdatePlan;

/**
 * Driving port: apply a single artifact update described by an {@link UpdatePlan}.
 */
public interface ApplyUpdateUseCase {

    /**
     * Download and apply the update described in {@code plan}.
     */
    void applyUpdate(UpdatePlan plan);

    /** Mark the current artifact set as already up-to-date. */
    void markUpToDate();

    /** Signal that the update is unavailable (server side). */
    void markUnavailable();
}
