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
    private boolean initialized = false;

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
        // Standard continuous-to-discrete pole placement for second-order optimal system:
        // Position spring stiffness kPos = omega_n^2
        // Velocity damping coefficient kVel = 2 * zeta * omega_n
        this.kPos = omega_n * omega_n;
        this.kVel = 2.0 * zeta * omega_n;
    }

    /**
     * Updates the LQR state and computes the delta angle step for this frame.
     *
     * @param errorDeg current angle error (degrees, positive = target is ahead)
     * @param dt       frame time delta in seconds
     * @return delta angle to apply this frame (degrees)
     */
    public double update(double errorDeg, double dt) {
        if (dt <= 0.0) return 0.0;

        if (!initialized) {
            prevError = errorDeg;
            initialized = true;
        }

        // Second-order state space dynamical model:
        // State x = [errorDeg, currentVelocity]^T
        // Desired dynamics: x_dot_1 = currentVelocity, x_dot_2 = -kPos * errorDeg - kVel * currentVelocity
        // Acceleration drives velocity toward minimizing error with critical damping:
        double acceleration = this.kPos * errorDeg - this.kVel * this.currentVelocity;
        this.currentVelocity += acceleration * dt;

        // Bounded velocity to prevent hyper-rotation jerk spikes
        double maxSpeed = 360.0; // deg/sec
        if (this.currentVelocity > maxSpeed) this.currentVelocity = maxSpeed;
        else if (this.currentVelocity < -maxSpeed) this.currentVelocity = -maxSpeed;

        double step = this.currentVelocity * dt;

        // Smooth continuous C^1 asymptotic convergence clamp (no step = 0 hard zeroing)
        if (Math.abs(errorDeg) < Math.abs(step)) {
            step = errorDeg * (1.0 - Math.exp(-12.0 * dt));
            this.currentVelocity *= Math.exp(-8.0 * dt);
        }

        prevError = errorDeg;
        return step;
    }

    public void reset() {
        this.currentVelocity = 0.0;
        this.prevError = 0.0;
        this.initialized = false;
    }

    public double getCurrentVelocity() {
        return currentVelocity;
    }
}
