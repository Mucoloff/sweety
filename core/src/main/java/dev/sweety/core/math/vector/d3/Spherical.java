package dev.sweety.core.math.vector.d3;

public record Spherical(double r, double theta, double phi) {

    public double[] toCartesian() {
        double sinPhi = Math.sin(phi);
        double x = r * sinPhi * Math.cos(theta);
        double y = r * Math.cos(phi);
        double z = r * sinPhi * Math.sin(theta);
        return new double[]{ x,y,z};
    }

    public static Spherical fromCartesian(double[] cartesian) {
        double r = Math.hypot(cartesian[0], Math.hypot(cartesian[1], cartesian[2]));
        double theta = Math.atan2(cartesian[2], cartesian[0]);
        double phi = Math.acos(cartesian[1] / r);
        return new Spherical(r, theta, phi);
    }
}