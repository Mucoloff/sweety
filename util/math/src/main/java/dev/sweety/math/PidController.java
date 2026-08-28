package dev.sweety.math;

/**
 * Reusable PID controller for smoothing a value toward a moving target (bounded angle/speed
 * corrections, not open-ended plants). Kept dependency-free (java.lang only) so it can be used
 * from any module without pulling in extra api surface.
 *
 * <p>Anti-windup: the integral accumulator is clamped to {@code [-integralClamp, integralClamp]}
 * so a long-saturated error (e.g. target unreachable for a while) can't leave a huge integral
 * term that then overshoots once the error clears.</p>
 */
public final class PidController {

    private final double kp;
    private final double ki;
    private final double kd;
    private final double integralClamp;

    private double integral;
    private double prevError;
    private boolean hasPrevError;

    public PidController(double kp, double ki, double kd) {
        this(kp, ki, kd, Double.MAX_VALUE);
    }

    public PidController(double kp, double ki, double kd, double integralClamp) {
        this.kp = kp;
        this.ki = ki;
        this.kd = kd;
        this.integralClamp = Math.abs(integralClamp);
    }

    /**
     * Advances the controller by one step and returns the correction to apply to the current
     * value this frame (caller does {@code current += update(error, dt)}).
     *
     * @param error signed distance from current value to target (caller resolves wrap-around, if any)
     * @param dt     elapsed time in seconds since the previous call (must be {@code > 0})
     */
    public double update(double error, double dt) {
        double safeDt = dt > 0d ? dt : 1e-6d;

        integral += error * safeDt;
        if (integral > integralClamp) integral = integralClamp;
        else if (integral < -integralClamp) integral = -integralClamp;

        double derivative = hasPrevError ? (error - prevError) / safeDt : 0d;
        prevError = error;
        hasPrevError = true;

        return kp * error + ki * integral + kd * derivative;
    }

    /**
     * Convenience overload for the common "move current toward target" call shape.
     * Equivalent to {@code update(target - current, dt)}.
     */
    public double update(double target, double current, double dt) {
        return update(target - current, dt);
    }

    /** Clears the integral accumulator and derivative history (call on target/mode changes). */
    public void reset() {
        integral = 0d;
        prevError = 0d;
        hasPrevError = false;
    }
}
