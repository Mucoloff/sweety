package dev.sweety.feature.transform;

/**
 * Obfuscation profiles dictating pipeline depth and performance trade-offs (§18 & §24 of style.md).
 */
public enum ObfuscationProfile {
    /**
     * Maximum security: Virtualization, Anti-Tamper, Control-Flow Flattening, Decoys, and Indy Obfuscation.
     */
    FULL,

    /**
     * Fast build & low overhead: Stripping, Integer Encoding, and Basic String Encryption.
     */
    LIGHTWEIGHT,

    /**
     * Specialized pipeline preserving Bukkit/Paper/BungeeCord event listeners and plugin manifests.
     */
    MINECRAFT_PLUGIN
}
