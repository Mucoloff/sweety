package dev.sweety.minecraft.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public enum MinecraftVersion implements Version {
    V_1_7_2(4), V_1_7_4(4), V_1_7_5(4),
    V_1_7_6(5), V_1_7_7(5), V_1_7_8(5), V_1_7_9(5), V_1_7_10(5),
    V_1_8(47), V_1_8_3(47), V_1_8_8(47),
    V_1_9(107), V_1_9_1(108), V_1_9_2(109), V_1_9_4(110),
    V_1_10(210), V_1_10_1(210), V_1_10_2(210),
    V_1_11(315), V_1_11_2(316),
    V_1_12(335), V_1_12_1(338), V_1_12_2(340),
    V_1_13(393), V_1_13_1(401), V_1_13_2(404),
    V_1_14(477), V_1_14_1(480), V_1_14_2(485), V_1_14_3(490), V_1_14_4(498),
    V_1_15(573), V_1_15_1(575), V_1_15_2(578),
    V_1_16(735), V_1_16_1(736), V_1_16_2(751), V_1_16_3(753),
    V_1_16_4(754), V_1_16_5(754),
    V_1_17(755), V_1_17_1(756),
    V_1_18(757), V_1_18_1(757), V_1_18_2(758),
    V_1_19(759), V_1_19_1(760), V_1_19_2(760), V_1_19_3(761), V_1_19_4(762),
    V_1_20(763), V_1_20_1(763), V_1_20_2(764),
    V_1_20_3(765), V_1_20_4(765),
    V_1_20_5(766), V_1_20_6(766),
    V_1_21(767), V_1_21_1(767), V_1_21_2(768), V_1_21_3(768),
    V_1_21_4(769), V_1_21_5(770), V_1_21_6(771),
    V_1_21_7(772), V_1_21_8(772), V_1_21_9(773), V_1_21_10(773), V_1_21_11(774),

    ERROR(-1, true);

    private static final MinecraftVersion[] VALUES = values();

    private final int protocolVersion;
    private final String releaseName;
    private final boolean error;

    MinecraftVersion(int protocolVersion) {
        this(protocolVersion, false);
    }

    MinecraftVersion(int protocolVersion, boolean error) {
        this.protocolVersion = protocolVersion;
        this.releaseName = error ? name() : name().substring(2).replace("_", ".");
        this.error = error;
    }

    public static @NotNull MinecraftVersion get(int protocolVersion) {
        int index = Arrays.binarySearch(VALUES, null, (v, key) -> {
            int p = v.protocolVersion();
            int target = (int) (Integer) protocolVersion;
            if (p == target) return 0;
            return p < target ? -1 : 1;
        });
        
        if (index < 0) return ERROR;
        
        // binarySearch might return any match, we usually want the latest for that protocol
        while (index + 1 < VALUES.length && VALUES[index + 1].protocolVersion() == protocolVersion) {
            index++;
        }
        return VALUES[index];
    }

    public static @NotNull MinecraftVersion get(@Nullable String name) {
        if (name == null) return ERROR;
        String clean = name.toUpperCase(Locale.ROOT).replace(".", "_");
        if (!clean.startsWith("V_")) clean = "V_" + clean;
        
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException e) {
            // Fallback for release names if they don't match exactly
            for (MinecraftVersion value : VALUES) {
                if (value.releaseName().equalsIgnoreCase(name)) return value;
            }
            return ERROR;
        }
    }

    public static @NotNull MinecraftVersion getLatest() {
        return VALUES[VALUES.length - 2];
    }

    @Override
    public int protocolVersion() {
        return protocolVersion;
    }

    @Override
    public @NotNull String releaseName() {
        return releaseName;
    }

    public boolean isError() {
        return error;
    }

    @Override
    public String toString() {
        return releaseName;
    }

    @Override
    public int compareTo(@NotNull Version o) {
        if (o instanceof MinecraftVersion other) {
            return Integer.compare(this.ordinal(), other.ordinal());
        }
        return Integer.compare(this.protocolVersion(), o.protocolVersion());
    }
}
