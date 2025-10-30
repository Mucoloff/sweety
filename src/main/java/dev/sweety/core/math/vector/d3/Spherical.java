package dev.sweety.core.math.vector.d3;

public record Spherical(double r, double theta, double phi) {

    public Vector3d toCartesian() {
        double sinPhi = Math.sin(phi);
        double x = r * sinPhi * Math.cos(theta);
        double y = r * Math.cos(phi);
        double z = r * sinPhi * Math.sin(theta);
        return new Vector3d(x, y, z);
    }
}