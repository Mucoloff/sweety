package dev.sweety.transform.transformers.inject;

import dev.sweety.transform.engine.Transformer;
import dev.sweety.transform.engine.TransformContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;
import java.util.Objects;

/**
 * Replaces placeholder LDC string constants injected into BuildInfo at compile time.
 *
 * Because javac inlines {@code public static final String} at every call site,
 * this transformer must run over every class in the skeleton JAR, not only
 * BuildInfo itself. For each class it:
 * <ol>
 *   <li>Scans all methods for {@code LDC "__PLACEHOLDER_X__"} and replaces with real value.</li>
 *   <li>If the class is BuildInfo, also updates {@code ConstantValue} attributes on fields.</li>
 * </ol>
 *
 * Usage (server-side, before JAR delivery):
 * <pre>
 *   TransformPipeline pipeline = TransformPipeline.builder()
 *       .add(new BuildInfoInjector(Map.of(
 *           BuildInfoInjector.LICENSE_KEY,      actualKey,
 *           BuildInfoInjector.LICENSE_KEY_HASH, sha256(actualKey),
 *           BuildInfoInjector.DISCORD_ID,       discordId,
 *           BuildInfoInjector.HANDSHAKE_SALT,   salt,
 *           BuildInfoInjector.EDITION,          "PRO",   // CUSTOMER | PRO | BETA
 *           BuildInfoInjector.ISSUED_TO,        licenseId,
 *           BuildInfoInjector.ISSUED_AT,        issuedAt
 *       )))
 *       .build();
 * </pre>
 */
public final class BuildInfoInjector extends Transformer {

    private static final String BUILD_INFO_INTERNAL         = "dev/sweety/bootstrap/build/BuildInfo";
    private static final String RUNTIME_BUILD_INFO_INTERNAL = "dev/sweety/runtime/build/RuntimeBuildInfo";

    // ── Placeholder constants (must match values in BuildInfo.java) ───────────
    // Only per-user fields injected at download time. Per-build fields (AUTH_SERVER_HOST,
    // AUTH_SERVER_PORT, MODULE_CHANNEL, UPDATE_SERVER_URL) are set via Gradle at build time.

    public static final String LICENSE_KEY      = "__LICENSE_KEY__";
    public static final String LICENSE_KEY_HASH = "__LICENSE_KEY_HASH__";
    public static final String DISCORD_ID       = "__DISCORD_ID__";
    public static final String DISCORD_AVATAR   = "__DISCORD_AVATAR__";
    public static final String HANDSHAKE_SALT   = "__HANDSHAKE_SALT__";
    public static final String EDITION          = "__EDITION__";
    public static final String ISSUED_TO        = "__ISSUED_TO__";
    public static final String ISSUED_AT        = "__ISSUED_AT__";

    /** Dedicated injection marker placeholder; rewritten to {@link #INJECTED_VALUE}. */
    public static final String INJECTED         = "__INJECTED__";
    /** Value written into {@link #INJECTED}; must match client BuildInfo.INJECTED_VALUE. */
    public static final String INJECTED_VALUE   = "luce-injected-v1";

    // ── State ─────────────────────────────────────────────────────────────────

    private final Map<String, String> replacements;

    /**
     * @param replacements map from placeholder string → real value.
     *                     Keys should be one of the public constants above.
     *                     Unknown keys are silently ignored.
     */
    public BuildInfoInjector(Map<String, String> replacements) {
        this.replacements = Map.copyOf(Objects.requireNonNull(replacements));
    }

    @Override
    public String name() { return "BuildInfoInjector"; }

    @Override
    public void transform(TransformContext ctx) {
        boolean isBuildInfo = BUILD_INFO_INTERNAL.equals(ctx.classNode().name)
                           || RUNTIME_BUILD_INFO_INTERNAL.equals(ctx.classNode().name);

        boolean anyReplaced = false;
        for (MethodNode mn : ctx.classNode().methods) {
            if (replaceLdc(mn)) anyReplaced = true;
        }

        if (isBuildInfo && anyReplaced) {
            updateConstantValues(ctx);
        }
    }

    // ── LDC replacement ───────────────────────────────────────────────────────

    private boolean replaceLdc(MethodNode mn) {
        boolean any = false;
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (insn.getOpcode() != Opcodes.LDC) continue;
            Object cst = ((LdcInsnNode) insn).cst;
            if (!(cst instanceof String s)) continue;
            String replacement = replacements.get(s);
            if (replacement == null) continue;
            ((LdcInsnNode) insn).cst = replacement;
            any = true;
        }
        return any;
    }

    // ── ConstantValue update (BuildInfo only) ─────────────────────────────────

    /**
     * Updates the ConstantValue attribute on each FieldNode in BuildInfo.
     * This ensures that any tool that reads the attribute directly (e.g., javap,
     * reflection via Field.get() on a static final after <clinit>) sees the real value.
     */
    private void updateConstantValues(TransformContext ctx) {
        for (FieldNode fn : ctx.classNode().fields) {
            if (fn.value instanceof String placeholder) {
                String replacement = replacements.get(placeholder);
                if (replacement != null) {
                    fn.value = replacement;
                }
            }
        }
    }
}
