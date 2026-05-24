package dev.sweety.transform;

import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.engine.transformer.control.ExceptionFlowTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.logging.*;
import java.util.zip.*;

/**
 * Command-line entry point for the transform pipeline.
 *
 * Usage:
 * <pre>
 *   java -jar EcstacyTransform-all.jar &lt;input.jar&gt; &lt;output.jar&gt; [--verbose]
 * </pre>
 *
 * Processes every {@code .class} entry in the input JAR through the default pipeline
 * and writes the result to the output JAR, preserving all non-class resources verbatim.
 *
 * If the ExceptionFlow transformer injected any sentinel classes, they are emitted
 * as additional entries in the output JAR.
 */
public final class TransformCLI {

    private static final Logger LOG = Logger.getLogger("EcstacyTransform");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: EcstacyTransform <input.jar> <output.jar> [--verbose]");
            System.exit(1);
        }

        final Path input   = Paths.get(args[0]);
        final Path output  = Paths.get(args[1]);
        final boolean verbose = args.length > 2 && "--verbose".equals(args[2]);

        if (verbose) {
            Logger.getLogger("EcstacyTransform").setLevel(Level.ALL);
        } else {
            Logger.getLogger("EcstacyTransform").setLevel(Level.WARNING);
        }

        if (!Files.exists(input)) {
            System.err.println("Input file not found: " + input);
            System.exit(1);
        }

        Files.createDirectories(output.getParent() == null ? Paths.get(".") : output.getParent());

        final TransformPipeline pipeline = TransformPipeline.Builder.defaultPipeline();

        // ── First pass: collect @Transform-annotated classes and compute transitive subclass closure ──
        // This ensures that if AbstractCheck has @Transform, all its subclasses (at any depth) are also
        // transformed — even if they don't carry the annotation directly.
        final String TRANSFORM_DESC = "Ldev/sweety/transform/annotation/Transform;";
        final Set<String> directlyAnnotated = new LinkedHashSet<>();
        final Map<String, String> superNameMap = new LinkedHashMap<>(); // className → superName

        try (JarFile firstScan = new JarFile(input.toFile())) {
            final Enumeration<JarEntry> entries = firstScan.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream in = firstScan.getInputStream(entry)) {
                    final ClassReader cr = new ClassReader(in.readAllBytes());
                    final ClassNode cn = new ClassNode(Opcodes.ASM9);
                    cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
                    if (cn.superName != null) superNameMap.put(cn.name, cn.superName);
                    if (hasAnnotation(cn.invisibleAnnotations, TRANSFORM_DESC)
                            || hasAnnotation(cn.visibleAnnotations, TRANSFORM_DESC)) {
                        directlyAnnotated.add(cn.name);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Transitive closure: keep adding classes whose superName is already in the set
        final Set<String> superTransformClasses = new LinkedHashSet<>(directlyAnnotated);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, String> e : superNameMap.entrySet()) {
                if (!superTransformClasses.contains(e.getKey())
                        && superTransformClasses.contains(e.getValue())) {
                    superTransformClasses.add(e.getKey());
                    changed = true;
                }
            }
        }

        pipeline.setSuperTransformClasses(superTransformClasses);
        if (verbose) System.out.println("[TransformCLI] Transform class set (" + superTransformClasses.size() + "): " + superTransformClasses);

        int classCount = 0;
        int transformedCount = 0;

        // Classes that had ExceptionFlow applied — need sentinel class emitted
        final Set<String> needSentinel = new LinkedHashSet<>();

        try (JarFile jarIn  = new JarFile(input.toFile());
             JarOutputStream jarOut = new JarOutputStream(new BufferedOutputStream(
                     Files.newOutputStream(output)), readManifest(jarIn))) {

            final Enumeration<JarEntry> entries = jarIn.entries();

            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if ("META-INF/MANIFEST.MF".equals(entry.getName())) continue;

                final byte[] bytes;
                try (InputStream in = jarIn.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }

                final String name = entry.getName();

                if (name.endsWith(".class")) {
                    classCount++;
                    byte[] transformed;
                    try {
                        transformed = pipeline.transform(bytes, name);
                        if (transformed != bytes) transformedCount++;

                        // Check if this class needs a sentinel inner class
                        if (needsSentinel(transformed, bytes)) {
                            final String outerName = name.replace(".class", "").replace('/', '.');
                            needSentinel.add(name.replace(".class", ""));
                        }
                    } catch (Exception e) {
                        LOG.warning("Transform failed for " + name + ": " + e.getMessage());
                        transformed = bytes; // emit original
                    }

                    writeEntry(jarOut, name, transformed);
                } else {
                    writeEntry(jarOut, name, bytes);
                }
            }

            // Emit synthetic sentinel exception classes
            for (String outerInternal : needSentinel) {
                final String sentinelEntry = outerInternal + "$__F$.class";
                // Only emit if not already present in the jar
                if (jarIn.getEntry(sentinelEntry) == null) {
                    final byte[] sentinelBytes = ExceptionFlowTransformer.buildSentinelClass(outerInternal);
                    writeEntry(jarOut, sentinelEntry, sentinelBytes);
                    if (verbose) System.out.println("[TransformCLI] Emitted sentinel: " + sentinelEntry);
                }
            }

            jarOut.finish();
        }

        System.out.printf("[EcstacyTransform] Done — %d/%d classes transformed, %d sentinel(s) emitted%n",
                transformedCount, classCount, needSentinel.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean hasAnnotation(List<AnnotationNode> list, String desc) {
        if (list == null) return false;
        return list.stream().anyMatch(a -> desc.equals(a.desc));
    }

    private static Manifest readManifest(JarFile jar) throws IOException {
        Manifest mf = jar.getManifest();
        return mf != null ? mf : new Manifest();
    }

    private static void writeEntry(JarOutputStream jos, String name, byte[] data) throws IOException {
        final ZipEntry entry = new ZipEntry(name);
        entry.setSize(data.length);
        jos.putNextEntry(entry);
        jos.write(data);
        jos.closeEntry();
    }

    /**
     * Heuristic: if the transformed bytes are larger than the original (transformation
     * was applied), check for an ExceptionFlow sentinel reference in the class.
     */
    private static boolean needsSentinel(byte[] transformed, byte[] original) {
        // If class is unchanged, no sentinel needed
        if (transformed == original || Arrays.equals(transformed, original)) return false;
        // Check if the transformed class references the sentinel exception class
        final String pattern = "__F$";
        final String text = new String(transformed, java.nio.charset.StandardCharsets.ISO_8859_1);
        return text.contains(pattern);
    }
}
