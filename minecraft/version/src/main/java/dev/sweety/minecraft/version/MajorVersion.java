package dev.sweety.minecraft.version;

import org.jetbrains.annotations.NotNull;

public enum MajorVersion implements Version {
    V_1_7(MinecraftVersion.V_1_7_2),
    V_1_8(MinecraftVersion.V_1_8),
    V_1_9(MinecraftVersion.V_1_9),
    V_1_10(MinecraftVersion.V_1_10),
    V_1_11(MinecraftVersion.V_1_11),
    V_1_12(MinecraftVersion.V_1_12),
    V_1_13(MinecraftVersion.V_1_13),
    V_1_14(MinecraftVersion.V_1_14),
    V_1_15(MinecraftVersion.V_1_15),
    V_1_16(MinecraftVersion.V_1_16),
    V_1_17(MinecraftVersion.V_1_17),
    V_1_18(MinecraftVersion.V_1_18),
    V_1_19(MinecraftVersion.V_1_19),
    V_1_20(MinecraftVersion.V_1_20),
    V_1_21(MinecraftVersion.V_1_21);

    private final MinecraftVersion start;

    MajorVersion(MinecraftVersion start) {
        this.start = start;
    }

    @Override
    public int protocolVersion() {
        return start.protocolVersion();
    }

    @Override
    public @NotNull String releaseName() {
        return name().substring(2).replace("_", ".");
    }

    @Override
    public int major() {
        return start.major();
    }

    @Override
    public int minor() {
        return start.minor();
    }

    @Override
    public int patch() {
        return start.patch();
    }

    @Override
    public @NotNull MinecraftVersion specific() {
        return start;
    }

    public MinecraftVersion start() {
        return start;
    }
}
