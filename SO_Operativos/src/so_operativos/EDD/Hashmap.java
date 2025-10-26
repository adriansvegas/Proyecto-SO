/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package so_operativos.EDD;


import java.util.LinkedList; 
import java.util.Set;
import java.util.HashSet;
import java.util.Map; 
import java.util.Iterator; 
import java.util.Collection; 
import java.util.List; 
import java.util.ArrayList; 
import java.util.AbstractMap; 
import java.util.NoSuchElementException; 
import java.util.function.Predicate; 


/**
 *
 * @author Edgar
 */

/**
 
 */
public class Hashmap<K, V> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private LinkedList<Entry<K, V>>[] buckets;
    private int size;
    private final float loadFactor;

    @SuppressWarnings("unchecked")
    public Hashmap() {
        this.buckets = new LinkedList[DEFAULT_CAPACITY];
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            buckets[i] = new LinkedList<>();
        }
        this.size = 0;
        this.loadFactor = DEFAULT_LOAD_FACTOR;
    }

    private static class Entry<K, V> {
        final K key;
        V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() { return key; }
        public V getValue() { return value; }
        public void setValue(V value) { this.value = value; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry<?, ?> entry = (Entry<?, ?>) o;
            return key.equals(entry.key); // Compare keys only for map logic
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    private int getBucketIndex(K key) {
        // Handle potential negative hash codes correctly
        int hashCode = key.hashCode();
        // Use Math.abs only if the modulo result could be negative (Java's % can return negative)
        int index = hashCode % buckets.length;
        return index < 0 ? index + buckets.length : index;
        // Or simpler, ensuring non-negative before modulo:
        // return (hashCode & 0x7FFFFFFF) % buckets.length;
    }


    public synchronized V put(K key, V value) {
        if (key == null) throw new NullPointerException("Key cannot be null");
        if (value == null) throw new NullPointerException("Value cannot be null"); // Common map behavior

        if ((float) (size + 1) / buckets.length >= loadFactor) { // Check before adding
            resize();
        }

        int bucketIndex = getBucketIndex(key);
        LinkedList<Entry<K, V>> bucket = buckets[bucketIndex];
        if (bucket == null) { // Should not happen with constructor, but safe check
             bucket = new LinkedList<>();
             buckets[bucketIndex] = bucket;
        }


        for (Entry<K, V> entry : bucket) {
            if (entry.key.equals(key)) {
                V oldValue = entry.value;
                entry.value = value;
                return oldValue;
            }
        }

        bucket.add(new Entry<>(key, value));
        size++;
        return null;
    }

    public synchronized V get(K key) {
         if (key == null) return null;
         int bucketIndex = getBucketIndex(key);
         LinkedList<Entry<K, V>> bucket = buckets[bucketIndex];
         if (bucket == null) return null; // Bucket might not exist if capacity is 0 or error
         for (Entry<K, V> entry : bucket) {
             if (entry.key.equals(key)) {
                 return entry.value;
             }
         }
         return null;
    }

    public synchronized V remove(K key) {
        if (key == null) return null;
        int bucketIndex = getBucketIndex(key);
        LinkedList<Entry<K, V>> bucket = buckets[bucketIndex];
        if (bucket == null) return null;

        // --- INICIO CORRECCIÓN: Usar java.util.Iterator ---
        Iterator<Entry<K, V>> iterator = bucket.iterator();
        // --- FIN CORRECCIÓN ---
        while (iterator.hasNext()) {
            Entry<K, V> entry = iterator.next();
            if (entry.key.equals(key)) {
                iterator.remove(); // Use iterator's remove method
                size--;
                return entry.value;
            }
        }
        return null;
    }


    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized void clear() {
        // Clear each bucket
        for (int i = 0; i < buckets.length; i++) {
             if (buckets[i] != null) {
                 buckets[i].clear();
             }
         }
         // Reset size
        size = 0;
         // Optionally, resize back to default capacity if desired
         // @SuppressWarnings("unchecked")
         // LinkedList<Entry<K, V>>[] newBuckets = new LinkedList[DEFAULT_CAPACITY];
         // for (int i = 0; i < DEFAULT_CAPACITY; i++) {
         //     newBuckets[i] = new LinkedList<>();
         // }
         // buckets = newBuckets;
    }


    public synchronized boolean containsKey(K key) {
        if (key == null) return false;
        int bucketIndex = getBucketIndex(key);
        LinkedList<Entry<K, V>> bucket = buckets[bucketIndex];
        if (bucket == null) return false;
        for (Entry<K, V> entry : bucket) {
            if (entry.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    // --- Métodos para iterar (simplificados) ---

    public synchronized Set<K> keySet() {
        Set<K> keys = new HashSet<>();
        for (LinkedList<Entry<K, V>> bucket : buckets) {
             if (bucket != null) {
                for (Entry<K, V> entry : bucket) {
                    keys.add(entry.key);
                }
             }
        }
        return keys;
    }

    public synchronized Collection<V> values() {
        List<V> values = new ArrayList<>(); // Use standard ArrayList for return type
        for (LinkedList<Entry<K, V>> bucket : buckets) {
             if (bucket != null) {
                for (Entry<K, V> entry : bucket) {
                    values.add(entry.value);
                }
             }
        }
        return values;
    }

     public synchronized Set<Map.Entry<K, V>> entrySet() {
         Set<Map.Entry<K, V>> entries = new HashSet<>();
         for (LinkedList<Entry<K, V>> bucket : buckets) {
              if (bucket != null) {
                for (Entry<K, V> entry : bucket) {
                    entries.add(new AbstractMap.SimpleEntry<>(entry.key, entry.value));
                }
              }
         }
         return entries;
     }

    @SuppressWarnings("unchecked")
    private synchronized void resize() {
        int oldCapacity = buckets.length;
        // Prevent excessive resizing if capacity is huge, though unlikely for this project
        if (oldCapacity >= Integer.MAX_VALUE / 2) {
             System.err.println("WARN: Max map capacity reached, not resizing.");
             return;
         }
        int newCapacity = oldCapacity * 2;
        LinkedList<Entry<K, V>>[] oldBuckets = buckets;

        buckets = new LinkedList[newCapacity];
        for (int i = 0; i < newCapacity; i++) {
            buckets[i] = new LinkedList<>();
        }
        size = 0; // Reset size before rehashing

        for (LinkedList<Entry<K, V>> bucket : oldBuckets) {
             if (bucket != null) {
                for (Entry<K, V> entry : bucket) {
                    put(entry.key, entry.value); // Rehash using put
                }
             }
        }
         // Optional logging:
         // System.out.println("Hashmap resized to capacity: " + newCapacity);
    }

    // --- Iterator needed for removeIf ---
    // Using java.util.Iterator internally now
    private synchronized Iterator<Entry<K, V>> entryIterator() {
        return new EntryIterator();
    }

    // --- INICIO CORRECCIÓN: Implementar java.util.Iterator ---
    private class EntryIterator implements Iterator<Entry<K, V>> {
    // --- FIN CORRECCIÓN ---
        private int currentBucketIndex;
        private Iterator<Entry<K, V>> currentBucketIterator;
        private Entry<K, V> lastReturned;

        EntryIterator() {
            currentBucketIndex = -1;
            advanceToNextBucket(); // Initialize iterator
        }

        // Helper to move to the next non-empty bucket's iterator
        private void advanceToNextBucket() {
            lastReturned = null; // Cannot remove until next() is called
            if (currentBucketIterator != null && currentBucketIterator.hasNext()) {
                return; // Current iterator still has elements
            }
            currentBucketIndex++;
            while (currentBucketIndex < buckets.length) {
                 if (buckets[currentBucketIndex] != null && !buckets[currentBucketIndex].isEmpty()) {
                    currentBucketIterator = buckets[currentBucketIndex].iterator();
                    return; // Found next iterator
                 }
                currentBucketIndex++;
            }
            currentBucketIterator = null; // No more elements
        }


        @Override
        public boolean hasNext() {
            // Ensure we are positioned correctly if the current iterator was exhausted
            if (currentBucketIterator == null || !currentBucketIterator.hasNext()) {
                 advanceToNextBucket(); // Try to find the next element
             }
             return currentBucketIterator != null && currentBucketIterator.hasNext();
        }

        @Override
        public Entry<K, V> next() {
            if (!hasNext()) { // hasNext() also advances the iterator if needed
                throw new NoSuchElementException();
            }
            lastReturned = currentBucketIterator.next();
            return lastReturned;
        }

        @Override
        public void remove() {
             if (lastReturned == null) {
                 throw new IllegalStateException("next() must be called before remove(), or remove() called twice");
             }
             // We need to remove from the underlying LinkedList iterator that produced lastReturned.
             // The standard LinkedList iterator supports remove().
             // However, the 'currentBucketIterator' might have advanced if next() exhausted a bucket.
             // A fully robust implementation is complex. This simplified version assumes
             // remove() is called immediately after next() and relies on the LinkedList iterator's state.
             // We'll find the *original* bucket iterator again - less efficient but safer for this structure.

             int bucketIdx = getBucketIndex(lastReturned.key);
             LinkedList<Entry<K,V>> bucket = buckets[bucketIdx];
             if (bucket != null) {
                 Iterator<Entry<K,V>> it = bucket.iterator();
                 while (it.hasNext()) {
                     Entry<K,V> current = it.next();
                     // Use == for comparison as lastReturned is the *exact* object from the list
                     if (current == lastReturned) {
                         it.remove();
                         size--;
                         lastReturned = null; // Prevent double remove
                         return;
                     }
                 }
             }
             // If we reach here, something went wrong (e.g., concurrent modification not fully handled)
             throw new IllegalStateException("Could not remove element, possibly due to concurrent modification or internal error.");
        }
    }

    // Simplified removeIf implementation using the corrected iterator
    public synchronized boolean removeIf(Predicate<Map.Entry<K, V>> filter) {
        boolean removed = false;
        Iterator<Entry<K, V>> it = entryIterator(); // Uses the internal EntryIterator
        while (it.hasNext()) {
             Entry<K, V> internalEntry = it.next();
             // Create a standard Map.Entry for the predicate, as the predicate expects it
             Map.Entry<K, V> mapEntry = new AbstractMap.SimpleEntry<>(internalEntry.key, internalEntry.value);
             if (filter.test(mapEntry)) {
                 it.remove(); // Use iterator's remove
                 removed = true;
             }
         }
         return removed;
    }

     // --- INICIO CORRECCIÓN: Remover interfaz interna ---
     // Remove the custom Iterator interface definition entirely
     // interface Iterator<E> { ... } // <<-- DELETE THIS WHOLE INTERFACE DEFINITION
     // --- FIN CORRECCIÓN ---
}