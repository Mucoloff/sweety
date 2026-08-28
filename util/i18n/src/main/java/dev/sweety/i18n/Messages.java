package dev.sweety.i18n;

import dev.sweety.config.common.Configuration;
import dev.sweety.config.yml.YamlConfiguration;
import dev.sweety.util.logger.SimpleLogger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Locale-aware message bundle backed by {@link Configuration}.
 *
 * <p>Lookup priority (first match wins):
 * <ol>
 *   <li>Disk {@code <overrideDir>/<lang>_<REGION>.<ext>}</li>
 *   <li>Disk {@code <overrideDir>/<lang>.<ext>}</li>
 *   <li>Classpath {@code messages_<lang>_<REGION>.<ext>}</li>
 *   <li>Classpath {@code messages_<lang>.<ext>}</li>
 *   <li>Classpath {@code messages_en.<ext>} — canonical English baseline</li>
 * </ol>
 *
 * <p>Missing keys: warn once, return the literal key — never throws.
 */
public final class Messages {

    private static final SimpleLogger LOG = SimpleLogger.of(Messages.class);
    private static final AtomicReference<Locale> OVERRIDE_LOCALE = new AtomicReference<>(null);
    private static final Map<String, Messages> CACHE = new ConcurrentHashMap<>();

    /** Merged string templates, highest-priority wins. Key = dotted path, value = template. */
    private final Map<String, String> templates;
    private final Locale locale;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    // kept for reload()
    private final String baseName;
    private final Configuration prototype;
    private final Path overrideDir;
    private final ClassLoader loader;

    private Messages(String baseName, Configuration prototype, Path overrideDir, Locale locale, ClassLoader loader) {
        this.baseName = baseName;
        this.prototype = prototype;
        this.overrideDir = overrideDir;
        this.locale = locale;
        this.loader = loader;
        this.templates = buildTemplates(baseName, prototype, overrideDir, locale, loader);
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    public static Messages forBundle(String baseName) {
        return forBundle(baseName, new YamlConfiguration(), null, Messages.class.getClassLoader());
    }

    /**
     * Load with an explicit class loader — REQUIRED when the bundle's {@code <baseName>_<lang>.yml}
     * lives in a different module/jar than this i18n library (e.g. the runtime jar on NeoForge's
     * module layers, where {@code Messages.class}'s loader can't see the runtime resources).
     */
    public static Messages forBundle(String baseName, ClassLoader loader) {
        return forBundle(baseName, new YamlConfiguration(), null, loader);
    }

    public static Messages forBundle(String baseName, Configuration prototype) {
        return forBundle(baseName, prototype, null, Messages.class.getClassLoader());
    }

    public static Messages forBundle(String baseName, Configuration prototype, Path overrideDir) {
        return forBundle(baseName, prototype, overrideDir, Messages.class.getClassLoader());
    }

    public static Messages forBundle(String baseName, Configuration prototype, Path overrideDir, ClassLoader loader) {
        String key = baseName + "@" + activeLocale().toLanguageTag() + "@" + System.identityHashCode(loader);
        return CACHE.computeIfAbsent(key, ignored -> new Messages(baseName, prototype, overrideDir, activeLocale(), loader));
    }

    // ── Global locale control ──────────────────────────────────────────────────

    public static void setDefaultLocale(Locale locale) {
        OVERRIDE_LOCALE.set(locale);
        CACHE.clear();
    }

    public static Locale activeLocale() {
        Locale override = OVERRIDE_LOCALE.get();
        return override != null ? override : Locale.getDefault();
    }

    // ── Instance API ───────────────────────────────────────────────────────────

    public Locale locale() {
        return locale;
    }

    public boolean has(String key) {
        return templates.containsKey(key);
    }

    public String get(String key, Object... args) {
        String template = templates.get(key);

        if (template == null) {
            if (warned.add(key)) LOG.warn("Missing message key '" + key + "' for locale " + locale.toLanguageTag());
            return key;
        }

        if (args == null || args.length == 0) return template;
        return MessageFormat.format(template, args);
    }

    public Messages reload() {
        String cacheKey = baseName + "@" + locale.toLanguageTag();
        Messages fresh = new Messages(baseName, prototype, overrideDir, locale, loader);
        CACHE.put(cacheKey, fresh);
        return fresh;
    }

    // ── Internal loading ───────────────────────────────────────────────────────

    private static Map<String, String> buildTemplates(
            String baseName, Configuration prototype, Path overrideDir, Locale locale, ClassLoader loader) {

        List<Configuration> layers = buildLayers(baseName, prototype, overrideDir, locale, loader);
        Map<String, String> out = new HashMap<>();

        for (int i = layers.size() - 1; i >= 0; i--) {
            for (Map.Entry<String, Object> e : layers.get(i).flatten().entrySet()) {
                if (e.getValue() instanceof String s) out.put(e.getKey(), s);
            }
        }
        return out;
    }

    /**
     * Builds the priority-ordered layer list (highest priority first).
     * Skips any resource that doesn't exist; list may be shorter than the candidate count.
     */
    private static List<Configuration> buildLayers(
            String baseName, Configuration prototype, Path overrideDir, Locale locale, ClassLoader loader) {

        List<String> candidates = buildCandidates(baseName, prototype.extension(), locale);
        List<Configuration> result = new ArrayList<>();

        // Highest priority: disk overrides (reversed so high-specificity is first)
        if (overrideDir != null) {
            for (int i = candidates.size() - 1; i >= 0; i--) {
                String name = candidates.get(i);
                Path diskFile = overrideDir.resolve(name);
                if (Files.isRegularFile(diskFile)) {
                    Configuration cfg = tryLoad(prototype, diskFile);
                    if (cfg != null) result.add(cfg);
                }
            }
        }

        // Lower priority: classpath (reversed so high-specificity is first)
        for (int i = candidates.size() - 1; i >= 0; i--) {
            String name = candidates.get(i);
            ClassLoader cl = loader != null ? loader : Messages.class.getClassLoader();
            InputStream stream = cl.getResourceAsStream(name);
            if (stream == null) stream = Messages.class.getClassLoader().getResourceAsStream(name);
            if (stream != null) {
                Configuration cfg = tryLoad(prototype, stream, name);
                if (cfg != null) result.add(cfg);
            }
        }

        return result;
    }

    /**
     * Candidate file names in ascending priority order (last = highest).
     * Always includes the en baseline; adds locale variants only when different from en.
     */
    private static List<String> buildCandidates(String baseName, String ext, Locale locale) {
        List<String> list = new ArrayList<>();
        list.add(baseName + "_en." + ext);

        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry().toUpperCase(Locale.ROOT);

        if (!lang.isEmpty() && !lang.equals("en")) {
            list.add(baseName + "_" + lang + "." + ext);
            if (!country.isEmpty()) {
                list.add(baseName + "_" + lang + "_" + country + "." + ext);
            }
        } else if (!country.isEmpty()) {
            list.add(baseName + "_en_" + country + "." + ext);
        }

        return list;
    }

    private static Configuration tryLoad(Configuration prototype, Path path) {
        try {
            Configuration cfg = newInstance(prototype);
            cfg.load(path);
            return cfg;
        } catch (Exception e) {
            LOG.warn("Could not load disk bundle '" + path + "': " + e.getMessage());
            return null;
        }
    }

    private static Configuration tryLoad(Configuration prototype, InputStream stream, String name) {
        try (stream) {
            Configuration cfg = newInstance(prototype);
            cfg.load(stream);
            return cfg;
        } catch (Exception e) {
            LOG.warn("Could not load classpath bundle '" + name + "': " + e.getMessage());
            return null;
        }
    }

    private static Configuration newInstance(Configuration prototype) {
        try {
            return prototype.getClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate " + prototype.getClass().getSimpleName(), e);
        }
    }
}
