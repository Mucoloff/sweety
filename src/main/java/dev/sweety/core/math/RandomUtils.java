package dev.sweety.core.math;

import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class RandomUtils {

    public final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

    public <E> E randomElement(Collection<? extends E> collection) {
        if (collection == null || collection.isEmpty()) return null;
        int index = RANDOM.nextInt(collection.size());
        if (collection instanceof List)
            return ((List<? extends E>) collection).get(index);
        Iterator<? extends E> iter = collection.iterator();
        for (int i = 0; i < index; i++) iter.next();
        return iter.next();
    }

    public static int range(int min, int max) {
        return RANDOM.nextInt(min, max+1);
    }

}
