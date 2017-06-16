package com.android.launcher3.reflection.predictor;

import android.support.annotation.NonNull;

public class b implements Comparable
{
    public String Ld;
    public float Le;

    public b(final String ld, final float le) {
        this.Ld = ld;
        this.Le = le;
    }

    @Override
    public int compareTo(@NonNull Object b) {
        if (b instanceof b) {
            return Float.compare(this.Le, ((b)b).Le);
        }
        return 0;
    }
}