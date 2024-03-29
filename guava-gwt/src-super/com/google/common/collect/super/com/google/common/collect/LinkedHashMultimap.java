/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.collect;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Implementation of {@code Multimap} that does not allow duplicate key-value
 * entries and that returns collections whose iterators follow the ordering in
 * which the data was added to the multimap.
 *
 * <p>The collections returned by {@code keySet}, {@code keys}, and {@code
 * asMap} iterate through the keys in the order they were first added to the
 * multimap. Similarly, {@code get}, {@code removeAll}, and {@code
 * replaceValues} return collections that iterate through the values in the
 * order they were added. The collections generated by {@code entries} and
 * {@code values} iterate across the key-value mappings in the order they were
 * added to the multimap.
 *
 * <p>The iteration ordering of the collections generated by {@code keySet},
 * {@code keys}, and {@code asMap} has a few subtleties. As long as the set of
 * keys remains unchanged, adding or removing mappings does not affect the key
 * iteration order. However, if you remove all values associated with a key and
 * then add the key back to the multimap, that key will come last in the key
 * iteration order.
 *
 * <p>The multimap does not store duplicate key-value pairs. Adding a new
 * key-value pair equal to an existing key-value pair has no effect.
 *
 * <p>Keys and values may be null. All optional multimap methods are supported,
 * and all returned views are modifiable.
 *
 * <p>This class is not threadsafe when any concurrent operations update the
 * multimap. Concurrent read operations will work correctly. To allow concurrent
 * update operations, wrap your multimap with a call to {@link
 * Multimaps#synchronizedSetMultimap}.
 *
 * <p>See the Guava User Guide article on <a href=
 * "http://code.google.com/p/guava-libraries/wiki/NewCollectionTypesExplained#Multimap">
 * {@code Multimap}</a>.
 *
 * @author Jared Levy
 * @author Louis Wasserman
 * @since 2.0 (imported from Google Collections Library)
 */
@GwtCompatible(serializable = true, emulated = true)
public final class LinkedHashMultimap<K, V> extends AbstractSetMultimap<K, V> {

  /**
   * Creates a new, empty {@code LinkedHashMultimap} with the default initial
   * capacities.
   */
  public static <K, V> LinkedHashMultimap<K, V> create() {
    return new LinkedHashMultimap<K, V>(DEFAULT_KEY_CAPACITY, DEFAULT_VALUE_SET_CAPACITY);
  }

  /**
   * Constructs an empty {@code LinkedHashMultimap} with enough capacity to hold
   * the specified numbers of keys and values without rehashing.
   *
   * @param expectedKeys the expected number of distinct keys
   * @param expectedValuesPerKey the expected average number of values per key
   * @throws IllegalArgumentException if {@code expectedKeys} or {@code
   *      expectedValuesPerKey} is negative
   */
  public static <K, V> LinkedHashMultimap<K, V> create(
      int expectedKeys, int expectedValuesPerKey) {
    return new LinkedHashMultimap<K, V>(
        Maps.capacity(expectedKeys),
        Maps.capacity(expectedValuesPerKey));
  }

  /**
   * Constructs a {@code LinkedHashMultimap} with the same mappings as the
   * specified multimap. If a key-value mapping appears multiple times in the
   * input multimap, it only appears once in the constructed multimap. The new
   * multimap has the same {@link Multimap#entries()} iteration order as the
   * input multimap, except for excluding duplicate mappings.
   *
   * @param multimap the multimap whose contents are copied to this multimap
   */
  public static <K, V> LinkedHashMultimap<K, V> create(
      Multimap<? extends K, ? extends V> multimap) {
    LinkedHashMultimap<K, V> result = create(multimap.keySet().size(), DEFAULT_VALUE_SET_CAPACITY);
    result.putAll(multimap);
    return result;
  }

  private interface ValueSetLink<K, V> {
    ValueSetLink<K, V> getPredecessorInValueSet();
    ValueSetLink<K, V> getSuccessorInValueSet();

    void setPredecessorInValueSet(ValueSetLink<K, V> entry);
    void setSuccessorInValueSet(ValueSetLink<K, V> entry);
  }

  private static <K, V> void succeedsInValueSet(ValueSetLink<K, V> pred, ValueSetLink<K, V> succ) {
    pred.setSuccessorInValueSet(succ);
    succ.setPredecessorInValueSet(pred);
  }

  private static <K, V> void succeedsInMultimap(
      ValueEntry<K, V> pred, ValueEntry<K, V> succ) {
    pred.setSuccessorInMultimap(succ);
    succ.setPredecessorInMultimap(pred);
  }

  private static <K, V> void deleteFromValueSet(ValueSetLink<K, V> entry) {
    succeedsInValueSet(entry.getPredecessorInValueSet(), entry.getSuccessorInValueSet());
  }

  private static <K, V> void deleteFromMultimap(ValueEntry<K, V> entry) {
    succeedsInMultimap(entry.getPredecessorInMultimap(), entry.getSuccessorInMultimap());
  }

  /**
   * LinkedHashMultimap entries are in no less than three coexisting linked lists:
   * a row in the hash table for a Set<V> associated with a key, the linked list
   * of insertion-ordered entries in that Set<V>, and the linked list of entries
   * in the LinkedHashMultimap as a whole.
   */
  private static final class ValueEntry<K, V> extends AbstractMapEntry<K, V>
      implements ValueSetLink<K, V> {
    final K key;
    final V value;
    final int valueHash;

    @Nullable ValueEntry<K, V> nextInValueSetHashRow;

    ValueSetLink<K, V> predecessorInValueSet;
    ValueSetLink<K, V> successorInValueSet;

    ValueEntry<K, V> predecessorInMultimap;
    ValueEntry<K, V> successorInMultimap;

    ValueEntry(@Nullable K key, @Nullable V value, int valueHash,
        @Nullable ValueEntry<K, V> nextInValueSetHashRow) {
      this.key = key;
      this.value = value;
      this.valueHash = valueHash;
      this.nextInValueSetHashRow = nextInValueSetHashRow;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public ValueSetLink<K, V> getPredecessorInValueSet() {
      return predecessorInValueSet;
    }

    @Override
    public ValueSetLink<K, V> getSuccessorInValueSet() {
      return successorInValueSet;
    }

    @Override
    public void setPredecessorInValueSet(ValueSetLink<K, V> entry) {
      predecessorInValueSet = entry;
    }

    @Override
    public void setSuccessorInValueSet(ValueSetLink<K, V> entry) {
      successorInValueSet = entry;
    }

    public ValueEntry<K, V> getPredecessorInMultimap() {
      return predecessorInMultimap;
    }

    public ValueEntry<K, V> getSuccessorInMultimap() {
      return successorInMultimap;
    }

    public void setSuccessorInMultimap(ValueEntry<K, V> multimapSuccessor) {
      this.successorInMultimap = multimapSuccessor;
    }

    public void setPredecessorInMultimap(ValueEntry<K, V> multimapPredecessor) {
      this.predecessorInMultimap = multimapPredecessor;
    }
  }

  private static final int DEFAULT_KEY_CAPACITY = 16;
  private static final int DEFAULT_VALUE_SET_CAPACITY = 2;

  private static final int MAX_VALUE_SET_TABLE_SIZE = Ints.MAX_POWER_OF_TWO;

  @VisibleForTesting transient int valueSetCapacity = DEFAULT_VALUE_SET_CAPACITY;
  private transient ValueEntry<K, V> multimapHeaderEntry;

  private LinkedHashMultimap(int keyCapacity, int valueSetCapacity) {
    super(new LinkedHashMap<K, Collection<V>>(keyCapacity));

    checkArgument(valueSetCapacity >= 0,
        "expectedValuesPerKey must be >= 0 but was %s", valueSetCapacity);

    this.valueSetCapacity = valueSetCapacity;
    this.multimapHeaderEntry = new ValueEntry<K, V>(null, null, 0, null);
    succeedsInMultimap(multimapHeaderEntry, multimapHeaderEntry);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates an empty {@code LinkedHashSet} for a collection of values for
   * one key.
   *
   * @return a new {@code LinkedHashSet} containing a collection of values for
   *     one key
   */
  @Override
  Set<V> createCollection() {
    return new LinkedHashSet<V>(valueSetCapacity);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a decorated insertion-ordered set that also keeps track of the
   * order in which key-value pairs are added to the multimap.
   *
   * @param key key to associate with values in the collection
   * @return a new decorated set containing a collection of values for one key
   */
  @Override
  Collection<V> createCollection(K key) {
    return new ValueSet(key, valueSetCapacity);
  }

  /**
   * {@inheritDoc}
   *
   * <p>If {@code values} is not empty and the multimap already contains a
   * mapping for {@code key}, the {@code keySet()} ordering is unchanged.
   * However, the provided values always come last in the {@link #entries()} and
   * {@link #values()} iteration orderings.
   */
  @Override
  public Set<V> replaceValues(K key, Iterable<? extends V> values) {
    return super.replaceValues(key, values);
  }

  /**
   * Returns a set of all key-value pairs. Changes to the returned set will
   * update the underlying multimap, and vice versa. The entries set does not
   * support the {@code add} or {@code addAll} operations.
   *
   * <p>The iterator generated by the returned set traverses the entries in the
   * order they were added to the multimap.
   *
   * <p>Each entry is an immutable snapshot of a key-value mapping in the
   * multimap, taken at the time the entry is returned by a method call to the
   * collection or its iterator.
   */
  @Override public Set<Map.Entry<K, V>> entries() {
    return super.entries();
  }

  /**
   * Returns a collection of all values in the multimap. Changes to the returned
   * collection will update the underlying multimap, and vice versa.
   *
   * <p>The iterator generated by the returned collection traverses the values
   * in the order they were added to the multimap.
   */
  @Override public Collection<V> values() {
    return super.values();
  }

  private final class ValueSet extends Sets.ImprovedAbstractSet<V> implements ValueSetLink<K, V> {
    /*
     * We currently use a fixed load factor of 1.0, a bit higher than normal to reduce memory
     * consumption.
     */

    private final K key;
    private ValueEntry<K, V>[] hashTable;
    private int size = 0;
    private int modCount = 0;

    // We use the set object itself as the end of the linked list, avoiding an unnecessary
    // entry object per key.
    private ValueSetLink<K, V> firstEntry;
    private ValueSetLink<K, V> lastEntry;

    ValueSet(K key, int expectedValues) {
      this.key = key;
      this.firstEntry = this;
      this.lastEntry = this;
      // Round expected values up to a power of 2 to get the table size.
      int tableSize = Integer.highestOneBit(Math.max(expectedValues, 2) - 1) << 1;
      if (tableSize < 0) {
        tableSize = MAX_VALUE_SET_TABLE_SIZE;
      }

      @SuppressWarnings("unchecked")
      ValueEntry<K, V>[] hashTable = new ValueEntry[tableSize];
      this.hashTable = hashTable;
    }

    @Override
    public ValueSetLink<K, V> getPredecessorInValueSet() {
      return lastEntry;
    }

    @Override
    public ValueSetLink<K, V> getSuccessorInValueSet() {
      return firstEntry;
    }

    @Override
    public void setPredecessorInValueSet(ValueSetLink<K, V> entry) {
      lastEntry = entry;
    }

    @Override
    public void setSuccessorInValueSet(ValueSetLink<K, V> entry) {
      firstEntry = entry;
    }

    @Override
    public Iterator<V> iterator() {
      return new Iterator<V>() {
        ValueSetLink<K, V> nextEntry = firstEntry;
        ValueEntry<K, V> toRemove;
        int expectedModCount = modCount;

        private void checkForComodification() {
          if (modCount != expectedModCount) {
            throw new ConcurrentModificationException();
          }
        }

        @Override
        public boolean hasNext() {
          checkForComodification();
          return nextEntry != ValueSet.this;
        }

        @Override
        public V next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          ValueEntry<K, V> entry = (ValueEntry<K, V>) nextEntry;
          V result = entry.getValue();
          toRemove = entry;
          nextEntry = entry.getSuccessorInValueSet();
          return result;
        }

        @Override
        public void remove() {
          checkForComodification();
          Iterators.checkRemove(toRemove != null);
          Object o = toRemove.getValue();
          int hash = (o == null) ? 0 : o.hashCode();
          int row = Hashing.smear(hash) & (hashTable.length - 1);
          ValueEntry<K, V> prev = null;
          for (ValueEntry<K, V> entry = hashTable[row]; entry != null;
               prev = entry, entry = entry.nextInValueSetHashRow) {
            if (entry == toRemove) {
              if (prev == null) {
                // first entry in row
                hashTable[row] = entry.nextInValueSetHashRow;
              } else {
                prev.nextInValueSetHashRow = entry.nextInValueSetHashRow;
              }
              deleteFromValueSet(toRemove);
              deleteFromMultimap(toRemove);
              size--;
              expectedModCount = ++modCount;
              break;
            }
          }
          toRemove = null;
        }
      };
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public boolean contains(@Nullable Object o) {
      int hash = (o == null) ? 0 : o.hashCode();
      int row = Hashing.smear(hash) & (hashTable.length - 1);

      for (ValueEntry<K, V> entry = hashTable[row]; entry != null;
          entry = entry.nextInValueSetHashRow) {
        if (hash == entry.valueHash && Objects.equal(o, entry.getValue())) {
          return true;
        }
      }
      return false;
    }

    @Override
    public boolean add(@Nullable V value) {
      int hash = (value == null) ? 0 : value.hashCode();
      int row = Hashing.smear(hash) & (hashTable.length - 1);

      ValueEntry<K, V> rowHead = hashTable[row];
      for (ValueEntry<K, V> entry = rowHead; entry != null;
          entry = entry.nextInValueSetHashRow) {
        if (hash == entry.valueHash && Objects.equal(value, entry.getValue())) {
          return false;
        }
      }

      ValueEntry<K, V> newEntry = new ValueEntry<K, V>(key, value, hash, rowHead);
      succeedsInValueSet(lastEntry, newEntry);
      succeedsInValueSet(newEntry, this);
      succeedsInMultimap(multimapHeaderEntry.getPredecessorInMultimap(), newEntry);
      succeedsInMultimap(newEntry, multimapHeaderEntry);
      hashTable[row] = newEntry;
      size++;
      modCount++;
      rehashIfNecessary();
      return true;
    }

    private void rehashIfNecessary() {
      if (size > hashTable.length && hashTable.length < MAX_VALUE_SET_TABLE_SIZE) {
        @SuppressWarnings("unchecked")
        ValueEntry<K, V>[] hashTable = new ValueEntry[this.hashTable.length * 2];
        this.hashTable = hashTable;
        int mask = hashTable.length - 1;
        for (ValueSetLink<K, V> entry = firstEntry;
              entry != this; entry = entry.getSuccessorInValueSet()) {
          ValueEntry<K, V> valueEntry = (ValueEntry<K, V>) entry;
          int row = Hashing.smear(valueEntry.valueHash) & mask;
          valueEntry.nextInValueSetHashRow = hashTable[row];
          hashTable[row] = valueEntry;
        }
      }
    }

    @Override
    public boolean remove(@Nullable Object o) {
      int hash = (o == null) ? 0 : o.hashCode();
      int row = Hashing.smear(hash) & (hashTable.length - 1);

      ValueEntry<K, V> prev = null;
      for (ValueEntry<K, V> entry = hashTable[row]; entry != null;
           prev = entry, entry = entry.nextInValueSetHashRow) {
        if (hash == entry.valueHash && Objects.equal(o, entry.getValue())) {
          if (prev == null) {
            // first entry in the row
            hashTable[row] = entry.nextInValueSetHashRow;
          } else {
            prev.nextInValueSetHashRow = entry.nextInValueSetHashRow;
          }
          deleteFromValueSet(entry);
          deleteFromMultimap(entry);
          size--;
          modCount++;
          return true;
        }
      }
      return false;
    }

    @Override
    public void clear() {
      Arrays.fill(hashTable, null);
      size = 0;
      for (ValueSetLink<K, V> entry = firstEntry;
           entry != this; entry = entry.getSuccessorInValueSet()) {
        ValueEntry<K, V> valueEntry = (ValueEntry<K, V>) entry;
        deleteFromMultimap(valueEntry);
      }
      succeedsInValueSet(this, this);
      modCount++;
    }
  }

  @Override
  Iterator<Map.Entry<K, V>> createEntryIterator() {
    return new Iterator<Map.Entry<K, V>>() {
      ValueEntry<K, V> nextEntry = multimapHeaderEntry.successorInMultimap;
      ValueEntry<K, V> toRemove;

      @Override
      public boolean hasNext() {
        return nextEntry != multimapHeaderEntry;
      }

      @Override
      public Map.Entry<K, V> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        ValueEntry<K, V> result = nextEntry;
        toRemove = result;
        nextEntry = nextEntry.successorInMultimap;
        return result;
      }

      @Override
      public void remove() {
        Iterators.checkRemove(toRemove != null);
        LinkedHashMultimap.this.remove(toRemove.getKey(), toRemove.getValue());
        toRemove = null;
      }
    };
  }

  @Override
  public void clear() {
    super.clear();
    succeedsInMultimap(multimapHeaderEntry, multimapHeaderEntry);
  }
}

