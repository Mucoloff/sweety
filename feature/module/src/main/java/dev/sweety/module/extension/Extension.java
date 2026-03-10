package dev.sweety.module.extension;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.service.ServiceHelper;
import dev.sweety.core.service.impl.ServiceManager;
import dev.sweety.core.service.ServiceRegistry;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@Getter
public abstract class Extension implements Toggleable, ServiceHelper {

    public static final String NAME = Extension.class.getSimpleName().toLowerCase();

    @NotNull
    protected final String name;

    @NotNull
    protected final File dataFolder;

    @NotNull
    protected final SimpleLogger logger;

    @NotNull
    protected final ServiceManager serviceManager = new ServiceManager();

    private boolean enabled;

    protected Extension(final @NotNull String name, @NotNull final File folder, @NotNull SimpleLogger logger) {
        this.dataFolder = new File(folder, this.name = name);
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

    @Override
    public ServiceRegistry manager() {
        return serviceManager;
    }

}