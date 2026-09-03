package dev.sweety.sql4j.document;

/**
 * Supported serialization formats for SQL4J Document Collections.
 */
public enum DocumentFormat {
    /**
     * Human-readable and configuration-friendly YAML format.
     */
    YAML,

    /**
     * Compact and universal JSON format.
     */
    JSON,

    /**
     * Raw text/string format without schema parsing.
     */
    RAW
}
