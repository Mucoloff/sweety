package dev.sweety.math.soa;

/**
 * Sample entity marked for Structure-of-Arrays (SoA) code generation.
 */
@SoA
public record ParticleSoA(
    int id,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    boolean active
) {
}
