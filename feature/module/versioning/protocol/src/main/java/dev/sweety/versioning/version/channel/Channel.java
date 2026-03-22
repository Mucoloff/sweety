package dev.sweety.versioning.version.channel;

import dev.sweety.core.util.PrettyEnum;

public enum Channel implements PrettyEnum {

    STABLE(0),
    BETA(1),
    DEV(2);

    private final int level;

    Channel(int level) {
        this.level = level;
    }

    public boolean accepts(Channel releaseChannel) {
        return this.level >= releaseChannel.level;
    }

}