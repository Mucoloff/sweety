package dev.sweety.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PidControllerTest {

    @Test
    void testPidFramerateInvariance() {
        // Run PID at 60 FPS and 240 FPS for 1 second; both should achieve stable convergence
        PidController pid60 = new PidController(10.0, 1.5, 0.5, 20.0);
        PidController pid240 = new PidController(10.0, 1.5, 0.5, 20.0);

        double target = 50.0;
        double current60 = 0.0;
        double current240 = 0.0;

        double dt60 = 1.0 / 60.0;
        for (int i = 0; i < 60; i++) {
            double error = target - current60;
            current60 += pid60.update(error, dt60) * dt60;
        }

        double dt240 = 1.0 / 240.0;
        for (int i = 0; i < 240; i++) {
            double error = target - current240;
            current240 += pid240.update(error, dt240) * dt240;
        }

        // Both framerates must converge toward the target without exploding
        assertTrue(current60 > 30.0 && current60 < 60.0, "60 FPS convergence in range");
        assertTrue(current240 > 30.0 && current240 < 60.0, "240 FPS convergence in range");
    }

    @Test
    void testPidAntiWindupClamping() {
        PidController pid = new PidController(5.0, 2.0, 0.1, 10.0);
        double largeError = 100.0;
        double dt = 0.1;

        // Saturate integral for 50 steps
        for (int i = 0; i < 50; i++) {
            pid.update(largeError, dt);
        }

        // Now reverse error; should recover quickly without waiting for unbounded integral to bleed off
        double correction = pid.update(-10.0, dt);
        assertTrue(correction < 50.0 * 5.0, "Anti-windup prevents runaway integral accumulator");
    }
}
