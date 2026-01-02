package dev.sweety.core.math.vector.d2;

import lombok.Getter;

import java.util.Objects;


/**
 * 2D int Vector.
 * This vector can represent coordinates, angles, or anything you want.
 * You can use this to represent an array if you really want.
 * PacketEvents usually uses this for block positions as they don't need any decimals.
 * Converted from Vector3i to 2i
 *
 * @author retrooper, mksweety
 * @since 1.7
 */

@Getter
public class Vector2i {

    private static final Vector2i ZERO = new Vector2i();

    /**
     * X (coordinate/angle/whatever you wish)
     */

    public final int x;

    /**
     * Y (coordinate/angle/whatever you wish)
     */

    public final int y;


    /**
     * Default constructor setting all coordinates/angles/values to their default values (=0).
     */

    public Vector2i() {
        this.x = 0;
        this.y = 0;
    }


    /**
     * Constructor allowing you to set the values.
     *
     * @param x X
     * @param y Y
     */

    public Vector2i(int x, int y) {
        this.x = x;
        this.y = y;
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
        if (array.length > 0) {
            x = array[0];
        } else {
            x = 0;
            y = 0;
            return;
        }
        if (array.length > 1) {
            y = array[1];
        } else {
            y = 0;
        }
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
        if (obj instanceof Vector2i vec) {
            return x == vec.x && y == vec.y;
        } else if (obj instanceof Vector2d vec) {
            return x == vec.x && y == vec.y;
        } else if (obj instanceof Vector2f vec) {
            return x == vec.x && y == vec.y;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    public Vector2d toVector3d() {
        return new Vector2d(x, y);
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
    public String toString() {
        return "X: " + x + ", Y: " + y;
    }
}