package dev.sweety.i18n;

import dev.sweety.config.common.Configuration;
import dev.sweety.config.yml.YamlConfiguration;
import dev.sweety.util.logger.SimpleLogger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
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

    private static final SimpleLogger LOG = new SimpleLogger(Messages.class);
    private static final AtomicReference<Locale> OVERRIDE_LOCALE = new AtomicReference<>(null);
    private static final Map<String, Messages> CACHE = new ConcurrentHashMap<>();

    /** Ordered highest-priority-first; first non-null result wins. */
    private final List<Configuration> layers;
    private final Locale locale;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    // kept for reload()
    private final String baseName;
    private final Configuration prototype;
    private final Path overrideDir;

    private Messages(String baseName, Configuration prototype, Path overrideDir, Locale locale) {
        this.baseName = baseName;
        this.prototype = prototype;
        this.overrideDir = overrideDir;
        this.locale = locale;
        this.layers = buildLayers(baseName, prototype, overrideDir, locale);
    }

    // ── Factories ──────────────────────────────────────────────────────────────

    public static Messages forBundle(String baseName) {
        return forBundle(baseName, new YamlConfiguration(), null);
    }

    public static Messages forBundle(String baseName, Configuration prototype) {
        return forBundle(baseName, prototype, null);
    }

    public static Messages forBundle(String baseName, Configuration prototype, Path overrideDir) {
        String key = baseName + "@" + activeLocale().toLanguageTag();
        return CACHE.computeIfAbsent(key, _ -> new Messages(baseName, prototype, overrideDir, activeLocale()));
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
        return layers.stream().anyMatch(cfg -> cfg.getString(key) != null);
    }

    public String get(String key, Object... args) {
        String template = null;
        for (Configuration layer : layers) {
            template = layer.getString(key);
            if (template != null) break;
        }

        if (template == null) {
            if (warned.add(key)) LOG.warn("Missing message key '" + key + "' for locale " + locale.toLanguageTag());
            return key;
        }

        if (args == null || args.length == 0) return template;
        return MessageFormat.format(template, args);
    }

    public Messages reload() {
        String cacheKey = baseName + "@" + locale.toLanguageTag();
        Messages fresh = new Messages(baseName, prototype, overrideDir, locale);
        CACHE.put(cacheKey, fresh);
        return fresh;
    }

    // ── Internal loading ───────────────────────────────────────────────────────

    /**
     * Builds the priority-ordered layer list (highest priority first).
     * Skips any resource that doesn't exist; list may be shorter than the candidate count.
     */
    private static List<Configuration> buildLayers(
            String baseName, Configuration prototype, Path overrideDir, Locale locale) {

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
            InputStream stream = Messages.class.getClassLoader().getResourceAsStream(name);
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
        list.add("messages_en." + ext);

        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry().toUpperCase(Locale.ROOT);

        if (!lang.isEmpty() && !lang.equals("en")) {
            list.add("messages_" + lang + "." + ext);
            if (!country.isEmpty()) {
                list.add("messages_" + lang + "_" + country + "." + ext);
            }
        } else if (!country.isEmpty()) {
            list.add("messages_en_" + country + "." + ext);
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
