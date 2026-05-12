package dev.sweety.extension;

import dev.sweety.util.logger.SimpleLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public abstract class Extension implements Toggleable {

    @NotNull
    private final String name, version;
    @Nullable
    private final String description;

    @NotNull
    private final Path dataFolder;

    @NotNull
    private final SimpleLogger logger;

    private boolean enabled;

    protected Extension(final @NotNull String name, @NotNull String version, @Nullable String description, @NotNull final Path folder, @NotNull SimpleLogger logger) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.dataFolder = folder.resolve(name);
        this.logger = logger;
    }

    @Override
    public final void toggle() {
        this.setEnabled(!this.enabled);
    }

    public final void setEnabled(final boolean enabled) {
        if (this.enabled == enabled) return;

        if (enabled) enable();
        else disable();

        this.enabled = enabled;
    }

    public @NotNull String name() {
        return name;
    }

    public @NotNull String version() {
        return version;
    }

    public @Nullable String description() {
        return description;
    }

    public @NotNull Path dataFolder() {
        return dataFolder;
    }

    public @NotNull SimpleLogger logger() {
        return logger;
    }

    public boolean enabled() {
        return enabled;
    }
}
