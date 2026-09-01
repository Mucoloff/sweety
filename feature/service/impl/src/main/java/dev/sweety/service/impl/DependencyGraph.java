package dev.sweety.service.impl;

import dev.sweety.service.DependsOn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DependencyGraph {

    public static List<Class<?>> computeStartOrder(Map<Class<?>, Object> services) {
        Map<Class<?>, Set<Class<?>>> adj = new HashMap<>();
        Map<Class<?>, Integer> inDegree = new HashMap<>();

        for (Class<?> clazz : services.keySet()) {
            adj.putIfAbsent(clazz, new HashSet<>());
            inDegree.putIfAbsent(clazz, 0);

            DependsOn dependsOn = clazz.getAnnotation(DependsOn.class);
            if (dependsOn != null) {
                for (Class<?> dep : dependsOn.value()) {
                    if (services.containsKey(dep)) {
                        adj.computeIfAbsent(dep, k -> new HashSet<>()).add(clazz);
                        inDegree.put(clazz, inDegree.getOrDefault(clazz, 0) + 1);
                    }
                }
            }
        }

        Deque<Class<?>> queue = new ArrayDeque<>();
        for (Map.Entry<Class<?>, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<Class<?>> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            Class<?> u = queue.poll();
            order.add(u);

            for (Class<?> v : adj.getOrDefault(u, Set.of())) {
                int deg = inDegree.get(v) - 1;
                inDegree.put(v, deg);
                if (deg == 0) {
                    queue.add(v);
                }
            }
        }

        // If cycle or unvisited, append remaining
        for (Class<?> clazz : services.keySet()) {
            if (!order.contains(clazz)) {
                order.add(clazz);
            }
        }

        return order;
    }
}
