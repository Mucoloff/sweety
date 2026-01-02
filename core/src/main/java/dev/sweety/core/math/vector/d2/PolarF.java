package dev.sweety.core.math.vector.d2;

public record PolarF(float r, float phi) {

    public Vector2f toCartesian() {
        float x = r * (float) Math.cos(phi);
        float y = r * (float) Math.sin(phi);
        return new Vector2f(x, y);
    }
}