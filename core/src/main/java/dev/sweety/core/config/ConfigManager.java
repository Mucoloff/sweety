package dev.sweety.core.config;

import dev.sweety.core.config.compression.GzipCompressor;
import dev.sweety.core.config.format.Format;
import dev.sweety.core.logger.SimpleLogger;
import com.google.gson.JsonElement;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ConfigManager<T> {

    private final File file;
    private final File directory;
    private final Format format;
    private final Class<T> type;
    private final boolean compressed;

    @Getter @Setter
    private T config;

    private final SimpleLogger logger = new SimpleLogger(ConfigManager.class);

    // Costruttore type-safe (ex ConfigHandler)
    public ConfigManager(File directory, T defaultConfig, String filename, boolean compressed) {
        this.config = defaultConfig;
        this.type = (Class<T>) defaultConfig.getClass();
        this.directory = directory;
        this.format = Format.JSON;
        this.file = new File(directory, filename + format.extension());
        this.compressed = compressed;
    }

    // Costruttore semplificato
    public ConfigManager(File directory, T defaultConfig, boolean compressed) {
        this(directory, defaultConfig, defaultConfig.getClass().getSimpleName().toLowerCase(), compressed);
    }

    // Costruttore per JsonElement (ex ConfigContainer)
    public static ConfigManager<JsonElement> json(File file, boolean compressed) {
        return json(file, new com.google.gson.JsonObject(), compressed);
    }

    public static ConfigManager<JsonElement> json(File file, JsonElement defaultValue, boolean compressed) {
        ConfigManager<JsonElement> manager = new ConfigManager<>(
            file.getParentFile(),
            defaultValue,
            file.getName().replaceFirst("[.][^.]+$", ""),
            compressed
        );
        manager.load();
        return manager;
    }

    // Salvataggio
    public void save() {
        ensureDirectoryExists();
        try {
            String json = format.write(config);
            byte[] data = compressed ? GzipCompressor.compress(json) : json.getBytes(StandardCharsets.UTF_8);
            FileUtils.writeByteArrayToFile(file, data);
        } catch (IOException e) {
            logger.error("Failed to save config to " + file, e);
        }
    }

    // Caricamento
    public void load() {
        ensureDirectoryExists();
        if (!file.exists()) {
            save();
            return;
        }

        try {
            byte[] raw = FileUtils.readFileToByteArray(file);
            String json = compressed ? GzipCompressor.decompress(raw) : new String(raw, StandardCharsets.UTF_8);
            this.config = format.read(json, type);
        } catch (IOException e) {
            logger.error("Failed to load config from " + file, e);
        }
    }

    // Metodo di convenienza per aggiornare e salvare
    public void update(T newConfig) {
        this.config = newConfig;
        save();
    }

    private void ensureDirectoryExists() {
        if (directory != null && !directory.exists()) {
            directory.mkdirs();
        }
    }
}
