package dev.sweety.core.math.vector;

import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import dev.sweety.core.math.vector.d2.Polar;
import dev.sweety.core.math.vector.d2.Vector2f;
import dev.sweety.core.math.vector.d3.Spherical;

public class CoordUtils {

    public static Polar pow(final Polar polar, final int n) {
        return new Polar(Math.pow(polar.r(), n), polar.phi() * n);
    }

    public static Spherical toSpherical(final Vector3d vec) {
        double r = Math.sqrt(vec.x * vec.x + vec.y * vec.y + vec.z * vec.z);
        double theta = Math.atan2(vec.z, vec.x);
        double phi = Math.acos(vec.y / r);
        return new Spherical(r, theta, phi);
    }

    public static String fromVec3d(Vector3d vec, String sep) {
        return "%s%s%s%s%s".formatted(vec.getX(), sep, vec.getY(), sep, vec.getZ());
    }

    public static Vector3d toVec3d(final String[] parts) {
        return new Vector3d(
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2])
        );
    }

    public static String fromVec3i(Vector3i vec, String sep) {
        return "%s%s%s%s%s".formatted(vec.getX(), sep, vec.getY(), sep, vec.getZ());
    }

    public static Vector3i toVec3i(final String[] parts) {
        return new Vector3i(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        );
    }

    public static String fromVec2f(Vector2f vec, String sep) {
        return "%s%s%s".formatted(vec.getX(), sep, vec.getY());
    }

    public static Vector2f toVec2f(final String[] parts) {
        return new Vector2f(
                Float.parseFloat(parts[0]),
                Float.parseFloat(parts[1])
        );
    }


}