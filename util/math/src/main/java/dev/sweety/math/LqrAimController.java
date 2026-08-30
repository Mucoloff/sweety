package dev.sweety.math;

/**
 * Enterprise Linear Quadratic Regulator (LQR) Optimal Aim Controller.
 * Controls angular tracking trajectories [error, angular_velocity] to achieve critically damped,
 * organic and Fitts's law compliant smooth aiming without mechanical oscillations or jerk spikes.
 */
public final class LqrAimController {

    private double kPos; // Position error feedback gain
    private double kVel; // Velocity damping feedback gain
    private double currentVelocity = 0.0;
    private double prevError = 0.0;

    /**
     * Constructs an LQR controller.
     *
     * @param responsiveness higher values yield faster target acquisition (similar to Q_pos)
     * @param dampingRatio   damping ratio (typically 1.0 for critically damped, no overshoot)
     */
    public LqrAimController(double responsiveness, double dampingRatio) {
        setGains(responsiveness, dampingRatio);
    }

    public void setGains(double responsiveness, double dampingRatio) {
        double omega_n = Math.max(0.1, responsiveness);
        double zeta = Math.max(0.1, dampingRatio);
        // Standard continuous-to-discrete pole placement for second-order optimal system
        this.kPos = omega_n * omega_n;
        this.kVel = 2.0 * zeta * omega_n;
    }

    /**
     * Updates the LQR state and computes the delta angle step for this frame.
     *
     * @param errorDeg current angle error (degrees)
     * @param dt       frame time delta in seconds
     * @return delta angle to apply this frame (degrees)
     */
    public double update(double errorDeg, double dt) {
        if (dt <= 0.0) return 0.0;

        // Estimate target relative angular velocity if dt is valid
        double measuredVelocity = (errorDeg - prevError) / dt;
        prevError = errorDeg;

        // Smooth estimated velocity to prevent noise spikes
        this.currentVelocity = this.currentVelocity * 0.7 + measuredVelocity * 0.3;

        // Optimal control law: u = - (kPos * error + kVel * velocity)
        // Integrated step over dt
        double acceleration = this.kPos * errorDeg - this.kVel * this.currentVelocity;
        this.currentVelocity += acceleration * dt;

        double step = this.currentVelocity * dt;

        // Asymptotic convergence clamp
        if (Math.abs(errorDeg) < Math.abs(step)) {
            step = errorDeg * 0.8;
            this.currentVelocity = 0.0;
        }

        return step;
    }

    public void reset() {
        this.currentVelocity = 0.0;
        this.prevError = 0.0;
    }

    public double getCurrentVelocity() {
        return currentVelocity;
    }
}
