/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.deletescape.lawnchair.util;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A utility map from keys to an ArrayList of values.
 */
public class MultiHashMap<K, V> extends HashMap<K, ArrayList<V>> {

    public MultiHashMap() {
    }

    public MultiHashMap(int size) {
        super(size);
    }

    public ArrayList<V> getOrCreate(K key) {
        ArrayList<V> list = get(key);
        if (list == null) {
            list = new ArrayList<>();
            put(key, list);
        }
        return list;
    }

    public void addToList(K key, V value) {
        ArrayList<V> list = getOrCreate(key);
        list.add(value);
    }

    public void addAllToList(K key, ArrayList<V> values) {
        ArrayList<V> list = getOrCreate(key);
        list.addAll(values);
    }

    @Override
    public MultiHashMap<K, V> clone() {
        MultiHashMap<K, V> map = new MultiHashMap<>(size());
        for (Entry<K, ArrayList<V>> entry : entrySet()) {
            map.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return map;
    }
}
