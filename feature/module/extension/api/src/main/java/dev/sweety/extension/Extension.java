package dev.sweety.extension;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class Extension implements Toggleable {

    @NotNull
    private final String name, version;
    @Nullable
    private final String description;

    @NotNull
    private final File dataFolder;

    @NotNull
    private final SimpleLogger logger; //todo

    private boolean enabled;

    protected Extension(final @NotNull String name, @NotNull String version, @Nullable String description, @NotNull final File folder, @NotNull SimpleLogger logger) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.dataFolder = new File(folder, name);
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

    public @NotNull File dataFolder() {
        return dataFolder;
    }

    public @NotNull SimpleLogger logger() {
        return logger;
    }

    public boolean enabled() {
        return enabled;
    }
}