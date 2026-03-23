package dev.sweety.core.math.list;

import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe, high-performance {@link Set} implementation backed by a {@link ConcurrentHashMap}.
 * <p>
 * This class implements the {@code Set} interface, backed by a ConcurrentHashMap instance.
 * It mimics the structure and API of {@link java.util.HashSet}, but is safe for concurrent use.
 * </p>
 * <p>
 * <b>Differences from HashSet:</b>
 * <ul>
 *     <li><b>Thread Safety:</b> All operations are thread-safe.</li>
 *     <li><b>Null Elements:</b> Unlike HashSet, this set <b>DOES NOT</b> support {@code null} elements,
 *     as {@code ConcurrentHashMap} does not permit null keys.</li>
 *     <li><b>Iterators:</b> Iterators are <i>weakly consistent</i>. They will never throw
 *     {@link java.util.ConcurrentModificationException} and may or may not show effects of modifications
 *     that occur during iteration.</li>
 * </ul>
 * </p>
 *
 * @param <K> the type of elements maintained by this set
 */
public class ConcurrentHashSet<K>
        extends AbstractSet<K>
        implements Set<K>, Cloneable, Serializable {

    @Serial
    private static final long serialVersionUID = 7249069246763182397L;

    private static final Object PRESENT = new Object();

    private transient ConcurrentHashMap<K, Object> map;

    /**
     * Constructs a new, empty set; the backing {@code ConcurrentHashMap} instance has
     * default initial capacity (16) and load factor (0.75).
     */
    public ConcurrentHashSet() {
        this.map = new ConcurrentHashMap<>();
    }

    /**
     * Constructs a new set containing the elements in the specified collection.
     * The backing {@code ConcurrentHashMap} instance is created with a default load factor (0.75)
     * and an initial capacity sufficient to contain the elements in the specified collection.
     *
     * @param c the collection whose elements are to be placed into this set
     * @throws NullPointerException if the specified collection is null
     */
    public ConcurrentHashSet(Collection<? extends K> c) {
        this.map = new ConcurrentHashMap<>(Math.max((int) (c.size() / 0.75f) + 1, 16));
        addAll(c);
    }

    /**
     * Constructs a new, empty set; the backing {@code ConcurrentHashMap} instance has
     * the specified initial capacity and default load factor (0.75).
     *
     * @param initialCapacity the initial capacity of the hash map
     * @throws IllegalArgumentException if the initial capacity is less than zero
     */
    public ConcurrentHashSet(int initialCapacity) {
        this.map = new ConcurrentHashMap<>(initialCapacity);
    }

    /**
     * Constructs a new, empty set; the backing {@code ConcurrentHashMap} instance has
     * the specified initial capacity and the specified load factor.
     *
     * @param initialCapacity the initial capacity of the hash map
     * @param loadFactor      the load factor of the hash map
     * @throws IllegalArgumentException if the initial capacity is less than zero, or if the load factor is nonpositive
     */
    public ConcurrentHashSet(int initialCapacity, float loadFactor) {
        this.map = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    }

    /**
     * Returns an iterator over the elements in this set. The elements are returned
     * in no particular order.
     * <p>
     * The returned iterator is <i>weakly consistent</i>.
     * </p>
     *
     * @return an Iterator over the elements in this set
     * @see ConcurrentHashMap#keySet()
     */
    @Override
    public @NotNull Iterator<K> iterator() {
        return map.keySet().iterator();
    }

    /**
     * Returns the number of elements in this set (its cardinality).
     *
     * @return the number of elements in this set (its cardinality)
     */
    @Override
    public int size() {
        return map.size();
    }

    /**
     * Returns {@code true} if this set contains no elements.
     *
     * @return {@code true} if this set contains no elements
     */
    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Returns {@code true} if this set contains the specified element.
     *
     * @param o element whose presence in this set is to be tested
     * @return {@code true} if this set contains the specified element
     */
    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    /**
     * Adds the specified element to this set if it is not already present.
     * <p>
     * Note: This implementation uses {@code putIfAbsent}, ensuring atomicity.
     * </p>
     *
     * @param e element to be added to this set
     * @return {@code true} if this set did not already contain the specified element
     * @throws NullPointerException if the specified element is null
     */
    @Override
    public boolean add(K e) {
        return map.putIfAbsent(e, PRESENT) == null;
    }

    /**
     * Removes the specified element from this set if it is present.
     *
     * @param o object to be removed from this set, if present
     * @return {@code true} if the set contained the specified element
     */
    @Override
    public boolean remove(Object o) {
        return map.remove(o) == PRESENT;
    }

    /**
     * Removes all of the elements from this set.
     * The set will be empty after this call returns.
     */
    @Override
    public void clear() {
        map.clear();
    }

    /**
     * Returns a shallow copy of this {@code ConcurrentHashSet} instance: the elements
     * themselves are not cloned.
     *
     * @return a shallow copy of this set
     */
    @Override
    public Object clone() {
        try {
            //noinspection unchecked
            ConcurrentHashSet<K> newSet = (ConcurrentHashSet<K>) super.clone();
            newSet.map = new ConcurrentHashMap<>(map);
            return newSet;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    @Override
    public Object @NotNull [] toArray() {
        return map.keySet().toArray();
    }

    @Override
    public <T> T @NotNull [] toArray(T @NotNull [] a) {
        return map.keySet().toArray(a);
    }

    /**
     * Saves the state of the {@code ConcurrentHashSet} instance to a stream (i.e., serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     */
    @Serial
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();

        // Write out size and elements (using a snapshot for consistency)
        Object[] keys = map.keySet().toArray();
        s.writeInt(keys.length);
        for (Object key : keys) {
            s.writeObject(key);
        }
    }

    /**
     * Reconstitutes the {@code ConcurrentHashSet} instance from a stream (i.e., deserializes it).
     *
     * @param s the stream
     * @throws java.io.IOException    if an I/O error occurs
     * @throws ClassNotFoundException if the class of a serialized object could not be found
     */
    @Serial
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();
        // Initialize the backing map
        map = new ConcurrentHashMap<>(size);

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++) {
            //noinspection unchecked
            K e = (K) s.readObject();
            map.put(e, PRESENT);
        }
    }
}
