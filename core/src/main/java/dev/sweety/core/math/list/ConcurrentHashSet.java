package dev.sweety.core.math.list;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConcurrentHashSet<K> implements Set<K> {
    private final ConcurrentMap<K, Boolean> _map;

    public ConcurrentHashSet() {
        this._map = new ConcurrentHashMap<>();
    }

    public ConcurrentHashSet(int initialCapacity) {
        this._map = new ConcurrentHashMap<>(initialCapacity);
    }

    public ConcurrentHashSet(Set<K> set) {
        this._map = new ConcurrentHashMap<>(set.size());
        for (K value : set) this._map.put(value, Boolean.TRUE);
    }

    public int size() {
        return this._map.size();
    }

    public boolean isEmpty() {
        return this._map.isEmpty();
    }

    public boolean contains(Object o) {
        return this._map.containsKey(o);
    }

    public Iterator<K> iterator() {
        return this._map.keySet().iterator();
    }

    public Object[] toArray() {
        return this._map.keySet().toArray();
    }

    public <T> T[] toArray(T[] a) {
        return this._map.keySet().toArray(a);
    }

    public boolean add(K o) {
        return this._map.putIfAbsent(o, Boolean.TRUE) == null;
    }

    public boolean remove(Object o) {
        return this._map.remove(o);
    }

    public boolean containsAll(Collection<?> c) {
        return this._map.keySet().containsAll(c);
    }

    public boolean addAll(Collection<? extends K> c) {
        boolean ret = false;

        for (K value : c) {
            ret |= this.add(value);
        }

        return ret;
    }

    public boolean retainAll(Collection<?> c) {
        return this._map.keySet().retainAll(c);
    }

    public boolean removeAll(Collection<?> c) {
        return this._map.keySet().removeAll(c);
    }

    public void clear() {
        this._map.clear();
    }

    public String toString() {
        return this._map.keySet().toString();
    }
}
