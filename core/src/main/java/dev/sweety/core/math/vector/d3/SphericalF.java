package dev.sweety.core.math.vector.d3;


public record SphericalF(float r, float theta, float phi) {

    public float[] toCartesian() {
        float sinPhi = (float) Math.sin(phi);
        float x = r * sinPhi * (float) Math.cos(theta);
        float y = r * (float) Math.cos(phi);
        float z = r * sinPhi * (float) Math.sin(theta);
        return new float[]{x, y, z};
    }

    public static SphericalF fromCartesian(float[] cartesian) {
        float r = (float) Math.hypot(cartesian[0], Math.hypot(cartesian[1], cartesian[2]));
        float theta = (float) Math.atan2(cartesian[2], cartesian[0]);
        float phi = (float) Math.acos(cartesian[1] / r);
        return new SphericalF(r, theta, phi);
    }
}