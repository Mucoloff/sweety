package dev.sweety.animation;

public class Animation {

    private Easing easing;
    // In seconds
    private double duration;
    private long start = -1;

    /**
     * Creates an animation instance.
     *
     * @param easing   the easing function
     * @param duration the duration in seconds
     */
    public Animation(Easing easing, double duration) {
        this.easing = easing;
        this.duration = duration;
    }

    public void start() {
        this.start = System.nanoTime();
    }

    public double progress() {
        if (start == -1) return 0;
        if (completed()) return 1;

        return this.easing.apply(passedSeconds() / this.duration);
    }

    public double passedSeconds() {
        return (System.nanoTime() - start) * 1.e-9;
    }

    public boolean completed() {
        return start != -1 && passedSeconds() >= this.duration;
    }

    public void forceEnd() {
        this.start = System.nanoTime() - (long) (duration * 1e9) - 1;
    }

    public Easing easing() {
        return easing;
    }

    public Animation easing(Easing easing) {
        this.easing = easing;
        return this;
    }

    public double duration() {
        return duration;
    }

    public Animation duration(double duration) {
        this.duration = duration;
        return this;
    }

}
