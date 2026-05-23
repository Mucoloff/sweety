package dev.sweety.extension.manager;

import dev.sweety.extension.Extension;
import dev.sweety.util.logger.SimpleLogger;

import java.nio.file.Path;

/**
 * Minimal concrete Extension used as the target class inside dynamically-built test jars.
 * It must be compiled to the test classpath so its .class bytes can be read and embedded.
 */
public class TestExtension extends Extension {

    public TestExtension(String name, String version, String description, Path folder, SimpleLogger logger) {
        super(name, version, description, folder, logger);
    }

    @Override
    public void enable() {}

    @Override
    public void disable() {}
}
