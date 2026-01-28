package dev.sweety.core.math.vector;

import dev.sweety.core.math.vector.d2.Polar;
import dev.sweety.core.math.vector.d2.Vector2f;

public class CoordUtils {

    public static Polar pow(final Polar polar, final int n) {
        return new Polar(Math.pow(polar.r(), n), polar.phi() * n);
    }

    public static String fromVec3d(String sep, double... vec) {
        return "%s%s%s%s%s".formatted(vec[0], sep, vec[1], sep, vec[2]);
    }

    public static double[] toVec3d(final String[] parts) {
        return new double[]{
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2])
        };
    }

    public static String fromVec3i(String sep, int... vec) {
        return "%s%s%s%s%s".formatted(vec[0], sep, vec[1], sep, vec[2]);
    }

    public static int[] toVec3i(final String[] parts) {
        return new int[]{
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        };
    }

    public static String fromVec2f(String sep, float... vec) {
        return "%s%s%s".formatted(vec[0], sep, vec[1]);
    }

    public static String fromVec2f(String sep, Vector2f vec) {
        return fromVec2f(sep, vec.x, vec.y);
    }

    public static Vector2f toVec2f(final String[] parts) {
        return new Vector2f(
                Float.parseFloat(parts[0]),
                Float.parseFloat(parts[1])
        );
    }

}