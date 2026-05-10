package dev.sweety.minecraft.version;

import org.jetbrains.annotations.NotNull;

public enum MajorVersion implements Version {
    V_1_7(MinecraftVersion.V_1_7_10),
    V_1_8(MinecraftVersion.V_1_8_8),
    V_1_9(MinecraftVersion.V_1_9_4),
    V_1_10(MinecraftVersion.V_1_10_2),
    V_1_11(MinecraftVersion.V_1_11_2),
    V_1_12(MinecraftVersion.V_1_12_2),
    V_1_13(MinecraftVersion.V_1_13_2),
    V_1_14(MinecraftVersion.V_1_14_4),
    V_1_15(MinecraftVersion.V_1_15_2),
    V_1_16(MinecraftVersion.V_1_16_5),
    V_1_17(MinecraftVersion.V_1_17_1),
    V_1_18(MinecraftVersion.V_1_18_2),
    V_1_19(MinecraftVersion.V_1_19_4),
    V_1_20(MinecraftVersion.V_1_20_6),
    V_1_21(MinecraftVersion.V_1_21_11);

    private final MinecraftVersion latest;

    MajorVersion(MinecraftVersion latest) {
        this.latest = latest;
    }

    @Override
    public int protocolVersion() {
        return latest.protocolVersion();
    }

    @Override
    public @NotNull String releaseName() {
        return latest.releaseName();
    }

    public MinecraftVersion latest() {
        return latest;
    }

    @Override
    public int compareTo(@NotNull Version o) {
        if (o instanceof MajorVersion other) {
            return Integer.compare(this.ordinal(), other.ordinal());
        }
        return Integer.compare(this.protocolVersion(), o.protocolVersion());
    }
}
