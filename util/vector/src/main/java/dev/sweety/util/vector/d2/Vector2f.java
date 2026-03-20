package dev.sweety.util.vector.d2;

/**
 * 2D float Vector.
 * This vector can represent coordinates, angles, or anything you want.
 * You can use this to represent an array if you really want.
 * Converted from Vector3f to 2f
 *
 * @param x X (coordinate/angle/whatever you wish)
 * @param y Y (coordinate/angle/whatever you wish)
 * @author retrooper, mksweety
 * @since 1.8
 */
public record Vector2f(float x, float y) {

    private static final Vector2f ZERO = new Vector2f();

    /**
     * Default constructor setting all coordinates/angles/values to their default values (=0).
     */
    public Vector2f() {
        this(0.0f, 0.0f);
    }

    /**
     * Constructor allowing you to set the values.
     *
     * @param x X
     * @param y Y
     */
    public Vector2f {
    }

    /**
     * Constructor allowing you to specify an array.
     * X will be set to the first index of an array(if it exists, otherwise 0).
     * Y will be set to the second index of an array(if it exists, otherwise 0).
     * Z will be set to the third index of an array(if it exists, otherwise 0).
     *
     * @param array Array.
     */
    public Vector2f(float[] array) {
        this(
                array.length > 0 ? array[0] : 0.0f,
                array.length > 1 ? array[1] : 0.0f
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

    public Vector2f add(float x, float y) {
        return new Vector2f(this.x + x, this.y + y);
    }

    public Vector2f add(Vector2f other) {
        return add(other.x, other.y);
    }

    public Vector2f subtract(float x, float y) {
        return new Vector2f(this.x - x, this.y - y);
    }

    public Vector2f subtract(Vector2f other) {
        return subtract(other.x, other.y);
    }

    public Vector2f multiply(float x, float y) {
        return new Vector2f(this.x * x, this.y * y);
    }

    public Vector2f multiply(Vector2f other) {
        return multiply(other.x, other.y);
    }

    public Vector2f multiply(float value) {
        return multiply(value, value);
    }

    public float dot(Vector2f other) {
        return this.x * other.x + this.y * other.y;
    }

    public Vector2f with(Float x, Float y) {
        return new Vector2f(x == null ? this.x : x, y == null ? this.y : y);
    }

    public Vector2f withX(float x) {
        return new Vector2f(x, this.y);
    }

    public Vector2f withY(float y) {
        return new Vector2f(this.x, y);
    }

    public Vector2d toVector2d() {
        return new Vector2d(x, y);
    }

    public Vector2i toVector2i() {
        return new Vector2i((int) x, (int) y);
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