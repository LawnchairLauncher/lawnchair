package ch.deletescape.lawnchair.util;

import java.util.HashMap;

public class MapHashMap<K1, K2, V> extends HashMap<K1, HashMap<K2, V>> {

    public HashMap<K2, V> getOrCreate(K1 key) {
        HashMap<K2, V> list = get(key);
        if (list == null) {
            list = new HashMap<>();
            put(key, list);
        }
        return list;
    }

    public void putAllToMap(K1 key, HashMap<K2, V> values) {
        HashMap<K2, V> map = getOrCreate(key);
        map.putAll(values);
    }
}
