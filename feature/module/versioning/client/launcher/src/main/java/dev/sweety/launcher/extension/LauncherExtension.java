package dev.sweety.launcher.extension;

//import dev.sweety.extension.Extension;
import dev.sweety.launcher.SweetyLauncher;
//import dev.sweety.util.logger.SimpleLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class LauncherExtension
        //extends Extension
{

    protected SweetyLauncher launcher;

    protected LauncherExtension(@NotNull String name, @NotNull String version, @Nullable String description, @NotNull File folder
                                //,@NotNull SimpleLogger logger
    ) {
        //super(name, version, description, folder, logger);
    }

    public void init(@NotNull SweetyLauncher launcher, @NotNull File file) {
        //super.init(file);
        this.launcher = launcher;
    }

    public abstract void onInitialize();
}
