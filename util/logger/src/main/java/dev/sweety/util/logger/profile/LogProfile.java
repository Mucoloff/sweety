package dev.sweety.util.logger.profile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.TimeUnit;

public final class LogProfile {
    private static final int MAX_CACHE_SIZE = 1000;

    private static final Cache<ProfileKey, LogProfile> CACHE = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private final String name;
    private final LogProfile parent;
    private final String fullPath;

    private LogProfile(ProfileKey key) {
        this(key.name(), key.parent());
    }

    private LogProfile(String name, LogProfile parent) {
        this.name = java.util.Objects.requireNonNull(name, "name cannot be null");
        this.parent = parent;
        this.fullPath = parent == null ? name : parent.fullPath + "@" + name;
    }

    public static LogProfile of(String name) {
        return of(name, null);
    }

    public static LogProfile of(@NotNull String name, LogProfile parent) {
        if (name.isEmpty()) throw new IllegalArgumentException("Profile name cannot be empty");
        return CACHE.get(new ProfileKey(name, parent), LogProfile::new);
    }

    @Override
    public String toString() {
        return fullPath;
    }

    // Identity-based key for caching
    private record ProfileKey(String name, LogProfile parent) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProfileKey that)) return false;
            return java.util.Objects.equals(name, that.name) && parent == that.parent;
        }

        @Override
        public int hashCode() {
            // Mix string hash with system identity hash of parent
            return 31 * java.util.Objects.hashCode(name) + (parent == null ? 0 : System.identityHashCode(parent));
        }
    }

    public String name() {
        return name;
    }

    public LogProfile parent() {
        return parent;
    }

    public String fullPath() {
        return fullPath;
    }
}



