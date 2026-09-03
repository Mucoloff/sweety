package dev.sweety.animation;

import java.util.function.DoubleUnaryOperator;

public enum Easing {

    // Linear
    EASE_LINEAR(x -> x),
    EASE_SQUARE(x -> -(x * x)),

    // luce
    EASE_LUCE(x -> Math.atan(x) * x),

    // Sinusoidal
    EASE_IN_SINE(x -> 1 - Math.cos((x * Math.PI) * 0.5)),
    EASE_OUT_SINE(x -> Math.sin((x * Math.PI) * 0.5)),
    EASE_IN_OUT_SINE(x -> -(Math.cos(Math.PI * x) - 1) * 0.5),

    // Quadratic
    EASE_IN_QUAD(x -> x * x),
    EASE_OUT_QUAD(x -> 1 - (1 - x) * (1 - x)),
    EASE_IN_OUT_QUAD(x -> x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) * 0.5),

    // Cubic
    EASE_IN_CUBIC(x -> x * x * x),
    EASE_OUT_CUBIC(x -> 1 - Math.pow(1 - x, 3)),
    EASE_IN_OUT_CUBIC(x -> x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) * 0.5),

    // Quartic
    EASE_IN_QUART(x -> x * x * x * x),
    EASE_OUT_QUART(x -> 1 - Math.pow(1 - x, 4)),
    EASE_IN_OUT_QUART(x -> x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) * 0.5),

    // Quintic
    EASE_IN_QUINT(x -> x * x * x * x * x),
    EASE_OUT_QUINT(x -> 1 - Math.pow(1 - x, 5)),
    EASE_IN_OUT_QUINT(x -> x < 0.5 ? 16 * x * x * x * x * x : 1 - Math.pow(-2 * x + 2, 5) * 0.5),

    // Exponential
    EASE_IN_EXPO(x -> x == 0 ? 0 : Math.pow(2, 10 * (x - 1))),
    EASE_OUT_EXPO(x -> x == 1 ? 1 : 1 - Math.pow(2, -10 * x)),
    EASE_IN_OUT_EXPO(x -> {
        if (x == 0 || x == 1)
            return x;
        if (x < 0.5f) return Math.pow(2, 20 * x - 10) * 0.5;
        return (2 - Math.pow(2, -20 * x + 10)) * 0.5;
    }),

    // Circolar
    EASE_IN_CIRC(x -> 1 - Math.sqrt(1 - Math.pow(x, 2))),
    EASE_OUT_CIRC(x -> Math.sqrt(1 - Math.pow(x - 1, 2))),
    EASE_IN_OUT_CIRC(x -> x < 0.5 ? (1 - Math.sqrt(1 - Math.pow(2 * x, 2))) * 0.5 : (Math.sqrt(1 - Math.pow(-2 * x + 2, 2)) + 1) * 0.5),

    // Back
    EASE_IN_BACK(x -> x * x * x - x * Math.sin(x * Math.PI)),
    EASE_OUT_BACK(x -> 1 + --x * x * x + x * Math.sin(x * Math.PI)),
    EASE_IN_OUT_BACK(x -> {
        double s = 1.70158;
        return x < 0.5 ? (Math.pow(2 * x, 2) * ((s + 1) * 2 * x - s)) * 0.5 : (Math.pow(2 * x - 2, 2) * ((s + 1) * (x * 2 - 2) + s) + 2) * 0.5;
    }),

    // Elastic
    EASE_IN_ELASTIC(x -> {
        if (x == 0 || x == 1)
            return x;
        return Math.pow(2, 10 * (x - 1)) * Math.sin((x - 1) * 5 * Math.PI);
    }),
    EASE_OUT_ELASTIC(x -> {
        if (x == 0 || x == 1)
            return x;
        return Math.pow(2, -10 * x) * Math.sin((x - 1) * 5 * Math.PI) + 1;
    }),
    EASE_IN_OUT_ELASTIC(x -> {
        if (x == 0 || x == 1)
            return x;
        double sin = Math.sin((x * 20 - 10) * Math.PI) * 0.5f;
        return x < 0.5 ? Math.pow(2, 20 * x - 10) * sin : Math.pow(2, -20 * x + 10) * sin + 1;
    }),

    // Bounce
    EASE_OUT_BOUNCE(x -> {
        if (x < (0.36363636363636365)) {
            return 7.5625 * x * x;
        } else if (x < (0.7272727272727273)) {
            return 7.5625 * (x -= (0.5454545454545454)) * x + 0.75;
        } else if (x < (0.9090909090909091)) {
            return 7.5625 * (x -= (0.8181818181818182)) * x + 0.9375;
        } else {
            return 7.5625 * (x -= (0.9545454545454546)) * x + 0.984375;
        }
    }),
    EASE_IN_BOUNCE(x -> 1d - EASE_OUT_BOUNCE.apply(1 - x)),
    EASE_IN_OUT_BOUNCE(x -> x < 0.5 ? EASE_IN_BOUNCE.apply(x * 2) * 0.5 : EASE_OUT_BOUNCE.apply(x * 2 - 1) * 0.5 + 0.5);

    public static final Easing[] VALUES = values();

    private final DoubleUnaryOperator easing;

    Easing(DoubleUnaryOperator easing) {
        this.easing = easing;
    }

    public double apply(double input) {
        return easing.applyAsDouble(input);
    }
}