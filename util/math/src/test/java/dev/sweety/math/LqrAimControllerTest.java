package dev.sweety.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LqrAimControllerTest {

    @Test
    void testLqrAimConvergenceWithoutOvershoot() {
        LqrAimController lqr = new LqrAimController(12.0, 1.0); // Critically damped
        double currentAngle = 0.0;
        double targetAngle = 45.0;
        double dt = 1.0 / 60.0; // 60 FPS

        for (int frame = 0; frame < 120; frame++) {
            double error = targetAngle - currentAngle;
            double step = lqr.update(error, dt);
            currentAngle += step;

            // Critically damped LQR should not overshoot target (45 deg)
            assertTrue(currentAngle <= targetAngle + 1e-4, "Angle must not overshoot target in critically damped mode");
        }

        // Must smoothly converge close to target
        assertEquals(targetAngle, currentAngle, 0.5, "LQR must converge within 0.5 degrees of target");
    }

    @Test
    void testLqrNoInstantaneousVelocityDiscontinuity() {
        LqrAimController lqr = new LqrAimController(14.0, 1.0);
        double currentAngle = 0.0;
        double targetAngle = 30.0;
        double dt = 1.0 / 120.0;
        double prevVel = 0.0;

        for (int frame = 0; frame < 240; frame++) {
            double error = targetAngle - currentAngle;
            double step = lqr.update(error, dt);
            currentAngle += step;
            double vel = lqr.getCurrentVelocity();

            if (frame > 2) {
                // Acceleration must remain bounded (no infinite deceleration jerk spike)
                double accel = Math.abs(vel - prevVel) / dt;
                assertTrue(accel < 50000.0, "Jerk/acceleration must remain bounded without discontinuous collapse");
            }
            prevVel = vel;
        }
    }
}
