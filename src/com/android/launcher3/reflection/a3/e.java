package com.android.launcher3.reflection.a3;

import com.android.launcher3.reflection.common.nano.a;

public class e extends b
{
    public com.android.launcher3.reflection.layers.b SV(final com.android.launcher3.reflection.common.b b, final a a) {
        final com.android.launcher3.reflection.layers.b b2 = new com.android.launcher3.reflection.layers.b(1, this.SW());
        b2.MC[com.android.launcher3.reflection.common.e.SS(a) - 1] = 1.0;
        return b2;
    }

    public int SW() {
        return 7;
    }

    public b clone() {
        return new e();
    }
}