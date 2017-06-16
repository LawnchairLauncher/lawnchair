package com.android.launcher3.reflection.a3;

import com.android.launcher3.reflection.common.nano.b;
import java.util.Comparator;

class h implements Comparator
{
    final /* synthetic */ k Mx;

    h(final k mx) {
        this.Mx = mx;
    }

    @Override
    public int compare(Object o1, Object o2) {
        if (o1 instanceof b && o2 instanceof b) {
            return Long.compare(((b)o1).LM, ((b)o2).LM);
        }
        return 0;
    }
}