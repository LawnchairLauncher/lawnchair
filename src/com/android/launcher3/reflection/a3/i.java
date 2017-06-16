package com.android.launcher3.reflection.a3;

import java.util.List;
import com.android.launcher3.reflection.common.c;
import com.android.launcher3.reflection.common.nano.a;

public class i extends b
{
    public com.android.launcher3.reflection.layers.b SV(final com.android.launcher3.reflection.common.b b, final a a) {
        final int n = 2;
        final int n2 = 1;
        final com.android.launcher3.reflection.layers.b b2 = new com.android.launcher3.reflection.layers.b(n2, this.SW());
        final List sa = com.android.launcher3.reflection.common.a.SA(a, "lat_long");
        if (sa.size() > 0) {
            final float[] so = c.SO(((com.android.launcher3.reflection.common.nano.b)sa.get(0)).LO[0], ((com.android.launcher3.reflection.common.nano.b)sa.get(0)).LO[n2]);
            b2.MC[0] = so[0];
            b2.MC[n2] = so[n2];
            b2.MC[n] = so[n];
        }
        return b2;
    }

    public int SW() {
        return 3;
    }

    public b clone() {
        return new i();
    }
}
