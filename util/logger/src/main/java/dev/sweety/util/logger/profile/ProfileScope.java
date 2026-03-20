package dev.sweety.util.logger.profile;

import dev.sweety.util.logger.SimpleLogger;

public class ProfileScope implements AutoCloseable {
    private final SimpleLogger logger;
    private boolean closed = false;

    public ProfileScope(SimpleLogger logger) {
        this.logger = logger;
    }

    @Override
    public void close() {
        if (!closed) {
            logger.pop();
            closed = true;
        }
    }
}
