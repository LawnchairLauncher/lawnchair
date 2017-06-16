package com.android.launcher3.reflection.a3;

import com.android.launcher3.reflection.common.e;
import com.android.launcher3.reflection.common.nano.a;

public class f extends b
{
    public com.android.launcher3.reflection.layers.b SV(final com.android.launcher3.reflection.common.b b, final a a) {
        final int n = 30;
        final int n2 = 23;
        final double n3 = 1.0;
        final com.android.launcher3.reflection.layers.b b2 = new com.android.launcher3.reflection.layers.b(1, this.SW());
        final int st = e.ST(a);
        b2.MC[st] = n3;
        final int su = e.SU(a);
        if (su >= n) {
            if (su > n) {
                int n4 = st + 1;
                if (n4 > n2) {
                    n4 = 0;
                }
                b2.MC[n4] = n3;
            }
        }
        else {
            int n5 = st - 1;
            if (n5 < 0) {
                n5 = n2;
            }
            b2.MC[n5] = n3;
        }
        return b2;
    }

    public int SW() {
        return 24;
    }

    public b clone() {
        return new f();
    }
}
