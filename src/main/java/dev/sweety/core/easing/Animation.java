package dev.sweety.core.easing;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Animation {

    @Setter
    private Easing easing;
    // In seconds
    @Setter
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

    public float getProgress() {
        if (isCompleted()) return 1;

        float passedSeconds = getPassedSeconds();
        float input = passedSeconds / this.duration;

        return this.easing.apply(input);
    }

    public float getPassedSeconds() {
        return (System.nanoTime() - start) / 1e9f;
    }

    public boolean isCompleted() {
        float passedSeconds = getPassedSeconds();
        return passedSeconds >= this.duration;
    }

}
