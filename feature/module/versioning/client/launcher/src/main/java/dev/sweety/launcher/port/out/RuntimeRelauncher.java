package dev.sweety.launcher.port.out;

import java.nio.file.Path;

/**
 * Driven port: relaunch or restart the managed process (e.g. app.jar).
 */
public interface RuntimeRelauncher {

    /**
     * Launch the process from the given jar and call {@code onExit} when it terminates.
     */
    void launch(Path jar, Runnable onExit) throws Exception;
}
