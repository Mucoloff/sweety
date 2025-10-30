package dev.sweety.core.math.vector;

import dev.sweety.core.math.vector.d2.Polar;
import dev.sweety.core.math.vector.d3.Spherical;
import dev.sweety.core.math.vector.d3.Vector3d;

public class CoordUtils {

    public static Polar pow(final Polar polar, final  int n) {
        return new Polar(Math.pow(polar.r(), n), polar.phi() * n);
    }

    public static Spherical toSpherical(final Vector3d vec) {
        double r = Math.sqrt(vec.x * vec.x + vec.y * vec.y + vec.z * vec.z);
        double theta = Math.atan2(vec.z, vec.x);
        double phi = Math.acos(vec.y / r);
        return new Spherical(r, theta, phi);
    }

}