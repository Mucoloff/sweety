package dev.sweety.animation;

public class Transitions {

    /**
     * @param value The current value
     * @param goal  The value to transition to
     * @param speed The speed of the operation (BIGGER = SLOWER!)
     * @return The new value
     */
    public static float transition(float value, float goal, float speed) {
        return transition(value, goal, speed, 0.02f);
    }

    public static float transition(float value, float goal, float speed, float skipSize) {
        float speed1 = speed < 1 ? 1 : speed;
        float diff = goal - value;
        float diffCalc = diff / speed1;
        if (Math.abs(diffCalc) < skipSize) diffCalc = diff;
        return value + diffCalc;
    }

    public static float easeOutExpo(float x) {
        return x < 0.5d ? 4 * x * x * x : (float) (1 - Math.pow(-2 * x + 2, 3) / 2d);
    }

    /**
     * Interpolates between two angles (degrees) along the shortest arc, wrapping at ±180°.
     *
     * @param current The current angle
     * @param target  The angle to interpolate toward
     * @param t       Interpolation factor (0..1)
     * @return The new angle, wrapped to (-180, 180]
     */
    public static float lerpAngle(float current, float target, float t) {
        float delta = ((target - current + 540f) % 360f) - 180f;
        return current + delta * t;
    }
}
