package dev.sweety.color;

import org.jetbrains.annotations.NotNull;

public record EColor(int rgba) {

    public EColor(byte r, byte g, byte b, byte a) {
        this(set(r, g, b, a));
    }

    public EColor(byte[] rgba) {
        this(set(rgba[0], rgba[1], rgba[2], rgba[3]));
    }

    public EColor(int r, int g, int b, int a) {
        this(set((byte) r, (byte) g, (byte) b, (byte) a));
    }

    public byte[] get() {
        return new byte[]{getR(), getG(), getB(), getA()};
    }

    public byte getR() {
        return (byte) ((rgba >> 24) & 0xFF);
    }

    public byte getG() {
        return (byte) ((rgba >> 16) & 0xFF);
    }

    public byte getB() {
        return (byte) ((rgba >> 8) & 0xFF);
    }

    public byte getA() {
        return (byte) (rgba & 0xFF);
    }

    public EColor withRed(final byte red) {
        return new EColor(withRed(rgba, red));
    }

    public EColor withRed(final int red) {
        return new EColor(withRed(rgba, red));
    }

    public EColor withRed(final float red) {
        return new EColor(withRed(rgba, red));
    }

    public EColor withGreen(final byte green) {
        return new EColor(withGreen(rgba, green));
    }

    public EColor withGreen(final int green) {
        return new EColor(withGreen(rgba, green));
    }

    public EColor withGreen(final float green) {
        return new EColor(withGreen(rgba, green));
    }

    public EColor withBlue(final byte blue) {
        return new EColor(withBlue(rgba, blue));
    }

    public EColor withBlue(final int blue) {
        return new EColor(withBlue(rgba, blue));
    }

    public EColor withBlue(final float blue) {
        return new EColor(withBlue(rgba, blue));
    }

    public EColor withAlpha(final byte alpha) {
        return new EColor(withAlpha(rgba, alpha));
    }

    public EColor withAlpha(final int alpha) {
        return new EColor(withAlpha(rgba, alpha));
    }

    public EColor withAlpha(final float alpha) {
        return new EColor(withAlpha(rgba, alpha));
    }

    public EColor darker() {
        return new EColor(darker(rgba));
    }

    public EColor darker(final float factor) {
        return new EColor(darker(rgba, factor));
    }

    public EColor lighter() {
        return new EColor(lighter(rgba));
    }

    public EColor lighter(final float factor) {
        return new EColor(lighter(rgba, factor));
    }

    public EColor mix(final EColor that, final float percent) {
        return new EColor(mix(this.rgba, that.rgba, percent));
    }

    public static int withRed(final int rgba, final byte red) {
        return rgba & 0x00FFFFFF | (red & 0xFF) << 24;
    }

    public static int withRed(final int rgba, final int red) {
        return withRed(rgba, (byte) red);
    }

    public static int withRed(final int rgba, final float red) {
        return withRed(rgba, (byte) Math.round(red * 255));
    }


    public static int withGreen(final int rgba, final byte green) {
        return rgba & 0xFF00FFFF | (green & 0xFF) << 16;
    }

    public static int withGreen(final int rgba, final int green) {
        return withRed(rgba, (byte) green);
    }

    public static int withGreen(final int rgba, final float green) {
        return withRed(rgba, (byte) Math.round(green * 255));
    }

    public static int withBlue(final int rgba, final byte blue) {
        return rgba & 0xFFFF00FF | (blue & 0xFF) << 8;
    }

    public static int withBlue(final int rgba, final int blue) {
        return withRed(rgba, (byte) blue);
    }

    public static int withBlue(final int rgba, final float blue) {
        return withRed(rgba, (byte) Math.round(blue * 255));
    }

    public static int withAlpha(final int rgba, final byte alpha) {
        return rgba & 0x00FFFFFF | (alpha & 0xFF) << 24;
    }

    public static int withAlpha(final int rgba, final int alpha) {
        return withAlpha(rgba, (byte) alpha);
    }

    public static int withAlpha(final int rgba, final float alpha) {
        return withAlpha(rgba, (byte) Math.round(alpha * 255));
    }

    public static int mix(final int rgba1, final int rgba2, final float percent) {
        final float inverse = 1.0f - percent;
        final int r = (int) (((rgba1 >> 16) & 0xFF) * inverse + ((rgba2 >> 16) & 0xFF) * percent);
        final int g = (int) (((rgba1 >> 8) & 0xFF) * inverse + ((rgba2 >> 8) & 0xFF) * percent);
        final int b = (int) (((rgba1) & 0xFF) * inverse + ((rgba2) & 0xFF) * percent);
        final int a = (int) (((rgba1 >> 24) & 0xFF) * inverse + ((rgba2 >> 24) & 0xFF) * percent);
        return set(r, g, b, a);
    }

    public static int darker(final int rgba) {
        return darker(rgba, 0.7f);
    }

    public static int darker(final int rgba, final float factor) {
        final int r = Math.max((int) (((rgba >> 16) & 0xFF) * factor), 0);
        final int g = Math.max((int) (((rgba >> 8) & 0xFF) * factor), 0);
        final int b = Math.max((int) (((rgba) & 0xFF) * factor), 0);
        final int a = (rgba >> 24) & 0xFF;
        return set(r, g, b, a);
    }

    public static int lighter(final int rgba) {
        return lighter(rgba, 0.7f);
    }

    public static int lighter(final int rgba, final float factor) {
        final int r = Math.min((int) (((rgba >> 16) & 0xFF) / factor), 255);
        final int g = Math.min((int) (((rgba >> 8) & 0xFF) / factor), 255);
        final int b = Math.min((int) (((rgba) & 0xFF) / factor), 255);
        final int a = (rgba >> 24) & 0xFF;
        return set(r, g, b, a);
    }

    private static int set(int r, int g, int b, int a) {
        return set((byte) r, (byte) g, (byte) b, (byte) a);
    }

    private static int set(byte r, byte g, byte b, byte a) {
        return ((r & 0xFF) << 24) |
                ((g & 0xFF) << 16) |
                ((b & 0xFF) << 8) |
                (a & 0xFF);
    }

    @Override
    public @NotNull String toString() {
        return String.format("#%02x%02x%02x", getR() & 0xFF, getG() & 0xFF, getB() & 0xFF);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EColor(int val))) return false;
        return this.rgba() == val;
    }

}