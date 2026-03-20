package dev.sweety.util.logger.profile;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public final class LogProfile {
    private static final int MAX_CACHE_SIZE = 1000;
    
    // LRU Cache: Removes eldest entry when size > MAX_CACHE_SIZE
    private static final Map<ProfileKey, LogProfile> CACHE = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ProfileKey, LogProfile> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        }
    );

    private final String name;
    private final LogProfile parent;
    private final String fullPath;

    private LogProfile(String name, LogProfile parent) {
        this.name = name;
        this.parent = parent;
        if (parent == null) {
            this.fullPath = name;
        } else {
            // Calculated once, immutable, thread-safe by default
            this.fullPath = parent.fullPath + "@" + name;
        }
    }

    public static LogProfile of(String name) {
        return of(name, null);
    }
    
    public static LogProfile of(String name, LogProfile parent) {
        if (name == null || name.isEmpty()) {
             throw new IllegalArgumentException("Profile name cannot be null or empty");
        }
        
        // Use composite key (identity of parent + string name)
        ProfileKey key = new ProfileKey(name, parent);
        
        // computeIfAbsent is thread-safe in synchronizedMap and handles the check-then-act
        return CACHE.computeIfAbsent(key, k -> new LogProfile(k.name, k.parent));
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
            // String comparison + Strict Reference Identity for parent
            return name.equals(that.name) && parent == that.parent;
        }

        @Override
        public int hashCode() {
            // Mix string hash with system identity hash of parent
            return 31 * name.hashCode() + (parent == null ? 0 : System.identityHashCode(parent));
        }
    }
}



