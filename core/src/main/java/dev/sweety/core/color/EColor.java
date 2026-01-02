package dev.sweety.core.color;

import lombok.Getter;
import lombok.Setter;

public class EColor {

    @Setter
    @Getter
    private int rgba;

    public EColor(int rgba) {
        this.rgba = rgba;
    }

    public EColor(byte r, byte g, byte b, byte a) {
        set(r, g, b, a);
    }

    public EColor(byte[] rgba){
        set(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    public EColor(int r, int g, int b, int a) {
        set((byte) r, (byte) g, (byte) b, (byte) a);
    }

    public byte[] get(){
        return new byte[]{getR(),getG(),getB(),getA()};
    }

    public static int mix(final int color1, final int color2, final float percent) {
        final float inverse = 1.0f - percent;
        final int r = (int) (((color1 >> 16) & 0xFF) * inverse + ((color2 >> 16) & 0xFF) * percent);
        final int g = (int) (((color1 >> 8) & 0xFF) * inverse + ((color2 >> 8) & 0xFF) * percent);
        final int b = (int) (((color1) & 0xFF) * inverse + ((color2) & 0xFF) * percent);
        final int a = (int) (((color1 >> 24) & 0xFF) * inverse + ((color2 >> 24) & 0xFF) * percent);
        return ((a & 0xFF) << 24) |
                ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8) |
                (b & 0xFF);
    }

    public static int withAlpha(final int color, final int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int withAlpha(final int color, final float alpha) {
        return withAlpha(color, (int) (alpha * 255));
    }

    public static int darker(final int color) {
        return darker(color, 0.7f);
    }

    public static int darker(final int color, final float factor) {
        final int r = Math.max((int) (((color >> 16) & 0xFF) * factor), 0);
        final int g = Math.max((int) (((color >> 8) & 0xFF) * factor), 0);
        final int b = Math.max((int) (((color) & 0xFF) * factor), 0);
        final int a = (color >> 24) & 0xFF;
        return ((a & 0xFF) << 24) |
                ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8) |
                (b & 0xFF);
    }

    public static int lighter(final int color) {
        return lighter(color, 0.7f);
    }

    public static int lighter(final int color, final float factor) {
        final int r = Math.min((int) (((color >> 16) & 0xFF) / factor), 255);
        final int g = Math.min((int) (((color >> 8) & 0xFF) / factor), 255);
        final int b = Math.min((int) (((color) & 0xFF) / factor), 255);
        final int a = (color >> 24) & 0xFF;
        return ((a & 0xFF) << 24) |
                ((r & 0xFF) << 16) |
                ((g & 0xFF) << 8) |
                (b & 0xFF);
    }

    public byte getR() {
        return (byte) ((rgba >> 24) & 0xFF);
    }

    public void setR(byte r) {
        rgba = (rgba & 0x00FFFFFF) | ((r & 0xFF) << 24);
    }

    public byte getG() {
        return (byte) ((rgba >> 16) & 0xFF);
    }

    public void setG(byte g) {
        rgba = (rgba & 0xFF00FFFF) | ((g & 0xFF) << 16);
    }

    public byte getB() {
        return (byte) ((rgba >> 8) & 0xFF);
    }

    public void setB(byte b) {
        rgba = (rgba & 0xFFFF00FF) | ((b & 0xFF) << 8);
    }

    public byte getA() {
        return (byte) (rgba & 0xFF);
    }

    public void setA(byte a) {
        rgba = (rgba & 0xFFFFFF00) | (a & 0xFF);
    }

    public void set(byte r, byte g, byte b, byte a) {
        rgba = ((r & 0xFF) << 24) |
                ((g & 0xFF) << 16) |
                ((b & 0xFF) << 8) |
                (a & 0xFF);
    }

    @Override
    public String toString() {
        return String.format("#%02x%02x%02x", getR() & 0xFF, getG() & 0xFF, getB() & 0xFF);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EColor)) return false;
        return this.rgba == ((EColor) obj).rgba;
    }

    @Override
    public int hashCode() {
        return rgba;
    }
}