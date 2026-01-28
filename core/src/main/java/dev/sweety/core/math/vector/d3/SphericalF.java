package dev.sweety.core.math.vector.d3;

import com.github.retrooper.packetevents.util.Vector3f;

public record SphericalF(float r, float theta, float phi) {

    public Vector3f toCartesian() {
        float sinPhi = (float) Math.sin(phi);
        float x = r * sinPhi * (float) Math.cos(theta);
        float y = r * (float) Math.cos(phi);
        float z = r * sinPhi * (float) Math.sin(theta);
        return new Vector3f(x, y, z);
    }

    public static SphericalF fromCartesian(Vector3f cartesian) {
        float r = (float) Math.hypot(cartesian.getX(), Math.hypot(cartesian.getY(), cartesian.getZ()));
        float theta = (float) Math.atan2(cartesian.getZ(), cartesian.getX());
        float phi = (float) Math.acos(cartesian.getY() / r);
        return new SphericalF(r, theta, phi);
    }
}