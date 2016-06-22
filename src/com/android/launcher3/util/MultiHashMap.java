package com.android.launcher3.util;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A utility map from keys to an ArrayList of values.
 */
public class MultiHashMap<K, V> extends HashMap<K, ArrayList<V>> {
    public void addToList(K key, V value) {
        ArrayList<V> list = get(key);
        if (list == null) {
            list = new ArrayList<>();
            list.add(value);
            put(key, list);
        } else {
            list.add(value);
        }
    }
}
