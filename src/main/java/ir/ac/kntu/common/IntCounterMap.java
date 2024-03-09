package ir.ac.kntu.common;

import java.util.*;

public class IntCounterMap<K> extends HashMap<K, Integer> {

    public IntCounterMap() {

    }

    public static <K> IntCounterMap<K> union(IntCounterMap<K> map1, IntCounterMap<K> map2) {
        IntCounterMap<K> union = new IntCounterMap<>();
        union.putAll(map1);
        union.union(map2);
        return union;
    }

    /**
     * adds incrValue to the previous value of this key and returns the new value
     *
     * @param key
     * @param incrValue
     * @return
     */
    public Integer incBy(K key, Integer incrValue) {
        final int newValue = getOrDefault(key, 0) + incrValue;
        put(key, newValue);

        return newValue;
    }

    /**
     * increments value by 1
     *
     * @param key
     * @return
     */
    public Integer inc(K key) {
        return incBy(key, 1);
    }

    public Integer dec(K key) {
        return decBy(key, 1);
    }

    /**
     * Subtracts decrValue to the previous value of this key and returns the new value
     *
     * @param key
     * @param decrValue
     * @return
     */
    public Integer decBy(K key, Integer decrValue) {
        final int newValue = getOrDefault(key, decrValue) - decrValue;
        put(key, newValue);

        return newValue;
    }

    /**
     * returns saved value for the key and if th ekey is not present
     * returns 0
     *
     * @param key
     * @return
     */
    @Override
    public Integer get(Object key) {
        return super.get(key) == null ? 0 : super.get(key);
    }

    /**
     * Return key set ordered according to their mapped values
     *
     * @param sortOrder
     * @return
     */
    public List<K> sortedKeySet(SortOrder sortOrder) {

        List<K> keys = new ArrayList<>(keySet());
        if (sortOrder == SortOrder.ASC)
            keys.sort(Comparator.comparingInt(this::get));
        else
            keys.sort((o1, o2) -> get(o2) - get(o1));

        return keys;
    }

    /**
     * Rteurn the most used key with its count
     *
     * @return
     */
    public Pair<K, Integer> getMaxKey() {
        K maxKey = null;
        int maxValue = -100000000;

        for (K k : keySet()) {
            final Integer value = get(k);
            if (value > maxValue) {
                maxValue = value;
                maxKey = k;
            }
        }

        return Pair.of(maxKey, maxValue);
    }

    /**
     * Finds whether this map have several keys that
     * have the same value, thus all of them can be
     * the MaxKey
     * @return 1 if only one unique max key is available, otherwise
     *  returns the number of similar keys
     */
    public int hasSeveralMaxKeys() {
        int maxKeysCount = 0;
        final Pair<K, Integer> maxKey = getMaxKey();
        for (Integer value : values()) {
            if (Objects.equals(value, maxKey.value))
                maxKeysCount++;
        }

        return maxKeysCount;
    }

    public IntCounterMap<K> copy() {
        IntCounterMap<K> copy = new IntCounterMap<>();
        copy.putAll(this);
        return copy;
    }

    public void union(IntCounterMap<K> otherMap) {
        otherMap.keySet().forEach(k -> this.incBy(k, otherMap.get(k)));
    }

    public int getItemsCount() {
        return values().stream().mapToInt(value -> value).sum();
    }
}
