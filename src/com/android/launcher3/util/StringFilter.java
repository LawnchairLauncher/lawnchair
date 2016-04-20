package com.android.launcher3.util;

import java.util.Set;

/**
 * Abstract class to filter a set of strings.
 */
public abstract class StringFilter {

    private StringFilter() { }

    public abstract boolean matches(String str);

    public static StringFilter matchesAll() {
        return new StringFilter() {
            @Override
            public boolean matches(String str) {
                return true;
            }
        };
    }

    public static StringFilter of(final Set<String> validEntries) {
        return new StringFilter() {
            @Override
            public boolean matches(String str) {
                return validEntries.contains(str);
            }
        };
    }
}
