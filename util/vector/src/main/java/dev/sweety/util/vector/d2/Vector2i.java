package dev.sweety.util.vector.d2;

import org.jetbrains.annotations.NotNull;

/**
 * 2D int Vector.
 * This vector can represent coordinates, angles, or anything you want.
 * You can use this to represent an array if you really want.
 * PacketEvents usually uses this for block positions as they don't need any decimals.
 * Converted from Vector3i to 2i
 *
 * @param x X (coordinate/angle/whatever you wish)
 * @param y Y (coordinate/angle/whatever you wish)
 * @author retrooper, mksweety
 * @since 1.7
 */

public record Vector2i(int x, int y) {

    private static final Vector2i ZERO = new Vector2i();

    /**
     * Default constructor setting all coordinates/angles/values to their default values (=0).
     */

    public Vector2i() {
        this(0, 0);
    }

    /**
     * Constructor allowing you to set the values.
     *
     * @param x X
     * @param y Y
     */

    public Vector2i {
    }

    /**
     * Constructor allowing you to specify an array.
     * X will be set to the first index of an array(if it exists, otherwise 0).
     * Y will be set to the second index of an array(if it exists, otherwise 0).
     * Z will be set to the third index of an array(if it exists, otherwise 0).
     *
     * @param array Array.
     */

    public Vector2i(int[] array) {
        this(
                array.length > 0 ? array[0] : 0,
                array.length > 1 ? array[1] : 0
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

    public Vector2i add(int x, int y) {
        return new Vector2i(this.x + x, this.y + y);
    }

    public Vector2i add(Vector2i other) {
        return add(other.x, other.y);
    }

    public Vector2i subtract(int x, int y) {
        return new Vector2i(this.x - x, this.y - y);
    }

    public Vector2i subtract(Vector2i other) {
        return subtract(other.x, other.y);
    }

    public Vector2i multiply(int x, int y) {
        return new Vector2i(this.x * x, this.y * y);
    }

    public Vector2i multiply(Vector2i other) {
        return multiply(other.x, other.y);
    }

    public Vector2i multiply(int value) {
        return multiply(value, value);
    }

    public int dot(Vector2i other) {
        return this.x * other.x + this.y * other.y;
    }

    public Vector2i with(Integer x, Integer y, Integer z) {
        return new Vector2i(x == null ? this.x : x, y == null ? this.y : y);
    }

    public Vector2i withX(int x) {
        return new Vector2i(x, this.y);
    }

    public Vector2i withY(int y) {
        return new Vector2i(this.x, y);
    }

    public Vector2d toVector2d() {
        return new Vector2d(x, y);
    }

    public Vector2f toVector2f() {
        return new Vector2f(x, y);
    }

    public Polar toPolar() {
        double r = Math.hypot(this.x, this.y);
        double phi = Math.atan2(this.y, this.x);
        return new Polar(r, phi);
    }

    @Override
    public @NotNull String toString() {
        return "X: " + x + ", Y: " + y;
    }
}