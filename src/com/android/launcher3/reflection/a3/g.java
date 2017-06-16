package com.android.launcher3.reflection.a3;

import java.util.Iterator;
import com.android.launcher3.reflection.common.nano.a;

public class g extends b
{
    public com.android.launcher3.reflection.layers.b SV(final com.android.launcher3.reflection.common.b b, final a a) {
        final int n = 1;
        final double n2 = 1.0;
        final com.android.launcher3.reflection.layers.b b2 = new com.android.launcher3.reflection.layers.b(n, 4);
        for (final Object o : com.android.launcher3.reflection.common.a.SA(a, "headset")) {
            com.android.launcher3.reflection.common.nano.b b3 = (com.android.launcher3.reflection.common.nano.b) o;
            int n3;
            if (a.LC - b3.LM >= 600000L) {
                n3 = n;
            }
            else {
                n3 = 0;
            }
            if (n3 == 0) {
                if (!b3.LK.equals("headset_wired_in")) {
                    if (!b3.LK.equals("headset_wired_out")) {
                        if (!b3.LK.equals("headset_bluetooth_in")) {
                            if (!b3.LK.equals("headset_bluetooth_out")) {
                                continue;
                            }
                            b2.MC[3] = n2;
                        }
                        else {
                            b2.MC[2] = n2;
                        }
                    }
                    else {
                        b2.MC[n] = n2;
                    }
                }
                else {
                    b2.MC[0] = n2;
                }
            }
        }
        return b2;
    }

    public int SW() {
        return 4;
    }

    public b clone() {
        return new g();
    }
}