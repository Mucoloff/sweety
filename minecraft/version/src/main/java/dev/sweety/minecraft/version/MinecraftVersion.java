package dev.sweety.minecraft.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public enum MinecraftVersion implements Version<MinecraftVersion> {
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

    /* SortedMap for Protocol-based range/exact lookups */
    private static final SortedMap<Integer, List<MinecraftVersion>> BY_PROTOCOL = new TreeMap<>();

    /* LinkedHashMap for Name-based lookups to maintain order */
    private static final Map<String, MinecraftVersion> BY_NAME = new LinkedHashMap<>();

    static {
        // Group by protocol into TreeMap
        Map<Integer, List<MinecraftVersion>> grouped = Arrays.stream(VALUES)
                .collect(Collectors.groupingBy(MinecraftVersion::protocolVersion));
        BY_PROTOCOL.putAll(grouped);

        // Populate LinkedHashMap
        for (MinecraftVersion value : VALUES) {
            BY_NAME.put(value.name().toUpperCase(Locale.ROOT), value);
            BY_NAME.put(value.releaseName().toUpperCase(Locale.ROOT), value);
        }
    }

    private final int protocolVersion;
    private final String releaseName;
    private final int major, minor, patch;
    private final boolean error;

    MinecraftVersion(int protocolVersion) {
        this(protocolVersion, false);
    }

    MinecraftVersion(int protocolVersion, boolean error) {
        this.protocolVersion = protocolVersion;
        this.error = error;
        this.releaseName = error ? name() : name().substring(2).replace("_", ".");

        if (error) {
            this.major = this.minor = this.patch = -1;
        } else {
            String[] split = releaseName.split("\\.");
            this.major = Integer.parseInt(split[0]);
            this.minor = Integer.parseInt(split[1]);
            this.patch = split.length > 2 ? Integer.parseInt(split[2]) : 0;
        }
    }

    @Override
    public @NotNull MinecraftVersion specific() {
        return this;
    }

    /**
     * Finds the latest version for a protocol using binary search.
     */
    public static @Nullable MinecraftVersion get(int protocolVersion) {
        List<MinecraftVersion> list = BY_PROTOCOL.get(protocolVersion);
        return list.isEmpty() ? null : list.getFirst();
    }

    public static @NotNull List<MinecraftVersion> getAll(int protocolVersion) {
        return BY_PROTOCOL.getOrDefault(protocolVersion, Collections.emptyList());
    }

    public static @NotNull MinecraftVersion get(@Nullable String name) {
        if (name == null) return ERROR;
        return BY_NAME.getOrDefault(name.toUpperCase(Locale.ROOT), ERROR);
    }

    @Override
    public int protocolVersion() {
        return protocolVersion;
    }

    @Override
    public @NotNull String releaseName() {
        return releaseName;
    }

    @Override
    public int major() {
        return major;
    }

    @Override
    public int minor() {
        return minor;
    }

    @Override
    public int patch() {
        return patch;
    }

    public boolean isError() {
        return error;
    }

    @Override
    public String toString() {
        return releaseName;
    }

}
