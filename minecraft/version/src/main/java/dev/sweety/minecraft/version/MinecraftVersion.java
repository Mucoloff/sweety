package dev.sweety.minecraft.version;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public enum MinecraftVersion {
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
    private static final Map<Integer, List<MinecraftVersion>> BY_PROTOCOL =
            Arrays.stream(VALUES)
                    .collect(Collectors.groupingBy(MinecraftVersion::getProtocolVersion));

    private static final Map<String, MinecraftVersion> BY_NAME_OR_RELEASE =
            Arrays.stream(VALUES)
                    .flatMap(v -> Arrays.stream(new String[]{
                            v.name().toUpperCase(Locale.ROOT),
                            v.getReleaseName()
                    }).map(key -> new AbstractMap.SimpleEntry<>(key, v)))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a
                    ));

    private static final List<MinecraftVersion> EMPTY = Collections.emptyList();

    private final int protocolVersion;
    private final String releaseName;

    MinecraftVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
        this.releaseName = name().substring(2).replace("_", ".");
    }

    MinecraftVersion(int protocolVersion, boolean isNotRelease) {
        this.protocolVersion = protocolVersion;
        this.releaseName = isNotRelease ? name() : name().substring(2).replace("_", ".");
    }

    public static List<MinecraftVersion> get(int protocolVersion) {
        return BY_PROTOCOL.getOrDefault(protocolVersion, EMPTY);
    }

    public static MinecraftVersion get(String name) {
        return BY_NAME_OR_RELEASE.getOrDefault(name.toUpperCase(), ERROR);
    }

    public static MinecraftVersion getLatest() {
        return VALUES[VALUES.length - 2];
    }

    public static MinecraftVersion getOldest() {
        return VALUES[0];
    }

}
