package dev.sweety.core.math.vector.d3;

public record SphericalF(float r, float theta, float phi) {

    public Vector3f toCartesian() {
        float sinPhi = (float) Math.sin(phi);
        float x = r * sinPhi * (float) Math.cos(theta);
        float y = r * (float) Math.cos(phi);
        float z = r * sinPhi * (float) Math.sin(theta);
        return new Vector3f(x, y, z);
    }
}