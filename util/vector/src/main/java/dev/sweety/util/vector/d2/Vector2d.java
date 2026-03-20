package dev.sweety.util.vector.d2;

/**
 * 2D double Vector.
 * This vector can represent coordinates, angles, or anything you want.
 * You can use this to represent an array if you really want.
 * Converted from Vector3d to 2d
 *
 * @param x X (coordinate/angle/whatever you wish)
 * @param y Y (coordinate/angle/whatever you wish)
 * @author retrooper, mksweety
 * @since 1.8
 */
public record Vector2d(double x, double y) {

    private static final Vector2d ZERO = new Vector2d();

    /**
     * Default constructor setting all coordinates/angles/values to their default values (=0).
     */
    public Vector2d() {
        this(0.0, 0.0);
    }

    /**
     * Constructor allowing you to set the values.
     *
     * @param x X
     * @param y Y
     */
    public Vector2d {
    }

    /**
     * Constructor allowing you to specify an array.
     * X will be set to the first index of an array(if it exists, otherwise 0).
     * Y will be set to the second index of an array(if it exists, otherwise 0).
     * Z will be set to the third index of an array(if it exists, otherwise 0).
     *
     * @param array Array.
     */
    public Vector2d(double[] array) {
        this(
                array.length > 0 ? array[0] : 0.0,
                array.length > 1 ? array[1] : 0.0
        );
    }

    /**
     * Is the object we are comparing to equal to us?
     * It must be of type Vector2d or Vector2i and all values must be equal to the values in this class.
     *
     * @param obj Compared object.
     * @return Are they equal?
     */
    @Override
    public boolean equals(Object obj) {
        return switch (obj) {
            case Vector2i(int _x, int _y) -> this.x() == _x && this.y() == _y;
            case Vector2d(double _x, double _y) -> this.x() == _x && this.y() == _y;
            case Vector2f(float _x, float _y) -> this.x() == _x && this.y() == _y;
            case null, default -> false;
        };
    }

    public Vector2d add(double x, double y) {
        return new Vector2d(this.x + x, this.y + y);
    }

    public Vector2d add(Vector2d other) {
        return add(other.x, other.y);
    }

    public Vector2d subtract(double x, double y) {
        return new Vector2d(this.x - x, this.y - y);
    }

    public Vector2d subtract(Vector2d other) {
        return subtract(other.x, other.y);
    }

    public Vector2d multiply(double x, double y) {
        return new Vector2d(this.x * x, this.y * y);
    }

    public Vector2d multiply(Vector2d other) {
        return multiply(other.x, other.y);
    }

    public Vector2d multiply(double value) {
        return multiply(value, value);
    }

    public double dot(Vector2d other) {
        return this.x * other.x + this.y * other.y;
    }

    public Vector2d with(Double x, Double y) {
        return new Vector2d(x == null ? this.x : x, y == null ? this.y : y);
    }

    public Vector2d withX(double x) {
        return new Vector2d(x, this.y);
    }

    public Vector2d withY(double y) {
        return new Vector2d(this.x, y);
    }

    public double distance(Vector2d other) {
        return Math.sqrt(distanceSquared(other));
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double lengthSquared() {
        return (x * x) + (y * y);
    }

    public Vector2d normalize() {
        double length = length();
        return new Vector2d(x / length, y / length);
    }

    public Vector2f toVector2f() {
        return new Vector2f((float) x, (float) y);
    }

    public Vector2i toVector2i() {
        return new Vector2i((int) x, (int) y);
    }

    public double distanceSquared(Vector2d other) {
        double distX = (x - other.x) * (x - other.x);
        double distY = (y - other.y) * (y - other.y);
        return distX + distY;
    }

    public Polar toPolar() {
        double r = Math.hypot(this.x, this.y);
        double phi = Math.atan2(this.y, this.x);
        return new Polar(r, phi);
    }

    @Override
    public String toString() {
        return "X: " + x + ", Y: " + y;
    }
}