package com.android.launcher3.reflection.a3;

import java.util.List;
import com.android.launcher3.reflection.common.nano.a;

public class j extends b
{
    public com.android.launcher3.reflection.layers.b SV(final com.android.launcher3.reflection.common.b b, final a a) {
        final int n = 1;
        final double n2 = 1.0;
        final com.android.launcher3.reflection.layers.b b2 = new com.android.launcher3.reflection.layers.b(n, 2);
        final List sa = com.android.launcher3.reflection.common.a.SA(a, "semantic_place");
        if (sa.size() > 0 && ((com.android.launcher3.reflection.common.nano.b)sa.get(0)).LN.length > 0) {
            if (!((com.android.launcher3.reflection.common.nano.b)sa.get(0)).LN[0].equals("Work")) {
                if (((com.android.launcher3.reflection.common.nano.b)sa.get(0)).LN[0].equals("Home")) {
                    b2.MC[n] = n2;
                }
            }
            else {
                b2.MC[0] = n2;
            }
        }
        return b2;
    }

    public int SW() {
        return 2;
    }

    public b clone() {
        return new j();
    }
}
