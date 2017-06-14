package com.android.launcher3.reflection;

import android.support.annotation.NonNull;

public class b_research implements Comparable
{
    public String Ld;
    public float Le;

    public b_research(final String ld, final float le) {
        this.Ld = ld;
        this.Le = le;
    }

    @Override
    public int compareTo(@NonNull Object b) {
        if (b instanceof b_research) {
            return Float.compare(this.Le, ((b_research)b).Le);
        }
        return 0;
    }
}
