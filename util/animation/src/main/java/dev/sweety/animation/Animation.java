package dev.sweety.animation;

public class Animation {

    private Easing easing;
    // In seconds
    private float duration;
    private long start;

    /**
     * Creates an animation instance.
     *
     * @param easing   the easing function
     * @param duration the duration in seconds
     */
    public Animation(Easing easing, float duration) {
        this.easing = easing;
        this.duration = duration;
    }

    public void start() {
        this.start = System.nanoTime();
    }

    public float progress() {
        if (completed()) return 1;

        float input = passedSeconds() / this.duration;

        return this.easing.apply(input);
    }

    public float passedSeconds() {
        return (System.nanoTime() - start) / 1e9f;
    }

    public boolean completed() {
        return passedSeconds() >= this.duration;
    }

    public Easing easing() {
        return easing;
    }

    public Animation easing(Easing easing) {
        this.easing = easing;
        return this;
    }

    public float duration() {
        return duration;
    }

    public Animation duration(float duration) {
        this.duration = duration;
        return this;
    }

}
