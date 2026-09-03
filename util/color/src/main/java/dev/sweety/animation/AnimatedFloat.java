package dev.sweety.animation;

/**
 * A float value that chases a retargetable goal over a fixed duration with an easing curve.
 * Unlike {@link Animation} (a fire-once, wall-clock {@code progress()} ramp started explicitly),
 * this is driven by an external per-frame delta and can be retargeted mid-flight — the shape
 * needed for hover/enabled/value transitions that change target every frame.
 */
public final class AnimatedFloat {

    private final double durationSeconds;
    private Easing easing;

    private float start;
    private float target;
    private float current;
    private double elapsed;

    private AnimatedFloat(float initial, double durationSeconds, Easing easing) {
        this.start = initial;
        this.target = initial;
        this.current = initial;
        this.durationSeconds = Math.max(0.0001, durationSeconds);
        this.easing = easing;
        this.elapsed = this.durationSeconds;
    }

    public static AnimatedFloat of(float initial, double durationSeconds, Easing easing) {
        return new AnimatedFloat(initial, durationSeconds, easing);
    }

    /** Retargets the animation; a no-op when the target has not moved. */
    public void target(float newTarget) {
        if (Math.abs(newTarget - target) < 0.0001f) {
            return;
        }
        start = current;
        target = newTarget;
        elapsed = 0;
    }

    /** Jumps straight to a value, cancelling any motion in flight. */
    public void set(float value) {
        start = value;
        target = value;
        current = value;
        elapsed = durationSeconds;
    }

    public void update(float deltaSeconds) {
        if (elapsed >= durationSeconds) {
            current = target;
            return;
        }
        elapsed = Math.min(durationSeconds, elapsed + deltaSeconds);
        double t = elapsed / durationSeconds;
        current = (float) (start + (target - start) * easing.apply(t));
    }

    public float value() {
        return current;
    }

    public float target() {
        return target;
    }

    public boolean finished() {
        return elapsed >= durationSeconds;
    }

    public Easing easing() {
        return easing;
    }

    public AnimatedFloat easing(Easing easing) {
        this.easing = easing;
        return this;
    }
}
