package com.android.launcher3.reflection.a3;

import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;

import com.android.launcher3.reflection.a3.d;
import com.android.launcher3.reflection.common.nano.a;

public class k extends d
{
    public k() {
        this.Mw = 5;
    }

    public k(final int n) {
        super(n);
        this.Mw = 5;
    }

    protected ArrayList Ti(final com.android.launcher3.reflection.common.b b, final a a, final long n, final int n2) {
        final ArrayList list = new ArrayList();
        final HashMap<Object, com.android.launcher3.reflection.common.d> hashMap = new HashMap<>();
        final List<com.android.launcher3.reflection.common.nano.b> sa = com.android.launcher3.reflection.common.a.SA(a, "app_usage");
        Collections.sort(sa, new h(this));
        for (final com.android.launcher3.reflection.common.nano.b b2 : sa) {
            int n3;
            if (a.LC - b2.LM > n) {
                n3 = 1;
            }
            else {
                n3 = 0;
            }
            if (n3 == 0) {
                final int th = this.Th(b2.LK, a.LC);
                com.android.launcher3.reflection.common.d d = hashMap.get(th);
                if (d == null) {
                    if (hashMap.size() >= n2) {
                        break;
                    }
                    d = new com.android.launcher3.reflection.common.d(th);
                    hashMap.put(th, d);
                }
                ++d.Mo;
            }
        }
        list.addAll(hashMap.values());
        return list;
    }

    public k clone() {
        final k k = new k(this.Mv);
        for (final Map.Entry<String, Integer> entry : this.Ms.entrySet()) {
            k.Ms.put(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<Integer, Long> entry2 : this.Mt.entrySet()) {
            k.Mt.put(entry2.getKey(), entry2.getValue());
        }
        k.Mu = Arrays.copyOf(this.Mu, this.Mu.length);
        k.Tc(this.Mp);
        return k;
    }
}