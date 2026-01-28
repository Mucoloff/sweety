package dev.sweety.core.math.vector.d3;

import com.github.retrooper.packetevents.util.Vector3d;

public record Spherical(double r, double theta, double phi) {

    public Vector3d toCartesian() {
        double sinPhi = Math.sin(phi);
        double x = r * sinPhi * Math.cos(theta);
        double y = r * Math.cos(phi);
        double z = r * sinPhi * Math.sin(theta);
        return new Vector3d(x, y, z);
    }

    public static Spherical fromCartesian(Vector3d cartesian) {
        double r = Math.hypot(cartesian.getX(), Math.hypot(cartesian.getY(), cartesian.getZ()));
        double theta = Math.atan2(cartesian.getZ(), cartesian.getX());
        double phi = Math.acos(cartesian.getY() / r);
        return new Spherical(r, theta, phi);
    }
}