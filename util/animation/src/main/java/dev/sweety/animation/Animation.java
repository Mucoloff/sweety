package dev.sweety.animation;

public class Animation {

    private Easing easing;
    // In seconds
    private double duration;
    private long start;

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
        if (completed()) return 1;

        double input = passedSeconds() / this.duration;

        return this.easing.apply(input);
    }

    public double passedSeconds() {
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

    public double duration() {
        return duration;
    }

    public Animation duration(double duration) {
        this.duration = duration;
        return this;
    }

}
