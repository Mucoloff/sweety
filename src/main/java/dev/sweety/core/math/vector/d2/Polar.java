package dev.sweety.core.math.vector.d2;

public record Polar(double r, double phi) {

    public Vector2d toCartesian() {
        double x = r * Math.cos(phi);
        double y = r * Math.sin(phi);
        return new Vector2d(x, y);
    }
}