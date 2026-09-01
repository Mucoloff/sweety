package dev.sweety.math.pool.leak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Netty-style resource leak detector for pooled objects (internal @Pooled classes and foreign classes alike).
 * Uses {@link PhantomReference} tracking to detect objects collected by GC without being properly released.
 */
public final class ResourceLeakDetector<T> {

    public enum Level {
        DISABLED,
        SIMPLE,
        PARANOID
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceLeakDetector.class);

    private static volatile Level defaultLevel;
    private static volatile Consumer<String> leakListener = null;

    static {
        String prop = System.getProperty("sweety.leakDetection", "DISABLED").trim().toUpperCase();
        try {
            defaultLevel = Level.valueOf(prop);
        } catch (IllegalArgumentException e) {
            defaultLevel = Level.DISABLED;
        }
    }

    private final Class<T> resourceType;
    private final int sampleInterval;
    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<>();
    private final Set<DefaultResourceLeak<?>> allLeaks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ResourceLeakDetector(Class<T> resourceType) {
        this(resourceType, 128);
    }

    public ResourceLeakDetector(Class<T> resourceType, int sampleInterval) {
        this.resourceType = resourceType;
        this.sampleInterval = sampleInterval;
    }

    public static Level getLevel() {
        return defaultLevel;
    }

    public static void setLevel(Level level) {
        defaultLevel = level != null ? level : Level.DISABLED;
    }

    public static void setLeakListener(Consumer<String> listener) {
        leakListener = listener;
    }

    public ResourceLeakTracker<T> track(T obj) {
        Level level = defaultLevel;
        if (level == Level.DISABLED || obj == null) {
            return null;
        }

        reportLeaks();

        if (level == Level.PARANOID || ThreadLocalRandom.current().nextInt(sampleInterval) == 0) {
            DefaultResourceLeak<T> leak = new DefaultResourceLeak<>(obj, refQueue, allLeaks, resourceType);
            allLeaks.add(leak);
            return leak;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public void reportLeaks() {
        DefaultResourceLeak<T> ref;
        while ((ref = (DefaultResourceLeak<T>) refQueue.poll()) != null) {
            ref.clear();
            if (allLeaks.remove(ref)) {
                if (!ref.closed.get()) {
                    String msg = "LEAK: Object of type " + ref.typeName + " was GC-collected without being released to pool!\n"
                            + "Acquired at:\n" + ref.creationRecord;
                    LOGGER.error(msg);
                    if (leakListener != null) {
                        leakListener.accept(msg);
                    }
                }
            }
        }
    }

    public interface ResourceLeakTracker<T> {
        void record();
        boolean close(T trackedObject);
    }

    private static final class DefaultResourceLeak<T> extends PhantomReference<Object> implements ResourceLeakTracker<T> {
        private final Set<DefaultResourceLeak<?>> allLeaks;
        private final String typeName;
        private final String creationRecord;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        DefaultResourceLeak(Object referent, ReferenceQueue<Object> q, Set<DefaultResourceLeak<?>> allLeaks, Class<T> type) {
            super(referent, q);
            this.allLeaks = allLeaks;
            this.typeName = type.getSimpleName();

            StringBuilder sb = new StringBuilder();
            StackTraceElement[] traces = Thread.currentThread().getStackTrace();
            // Skip top frames inside leak detector / pool
            int count = 0;
            for (int i = 3; i < traces.length && count < 8; i++) {
                sb.append("\tat ").append(traces[i].toString()).append("\n");
                count++;
            }
            this.creationRecord = sb.toString();
        }

        @Override
        public void record() {
            // Can record additional state if needed
        }

        @Override
        public boolean close(T trackedObject) {
            if (closed.compareAndSet(false, true)) {
                allLeaks.remove(this);
                clear();
                return true;
            }
            return false;
        }
    }
}
