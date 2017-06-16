package com.android.launcher3.reflection.common;

import java.util.Collection;
import com.android.launcher3.reflection.common.nano.b;
import java.util.ArrayList;
import java.util.List;

public class a
{
    public static List SA(final com.android.launcher3.reflection.common.nano.a a, final String s) {
        return SB(a, s, true);
    }

    public static List SB(final com.android.launcher3.reflection.common.nano.a a, final String s, final boolean b) {
        final ArrayList<b> list = new ArrayList<b>();
        final b[] li = a.LI;
        for (int length = li.length, i = 0; i < length; ++i) {
            final b b2 = li[i];
            if (b == b2.LL.equals(s)) {
                list.add(b2);
            }
        }
        return list;
    }

    public static void Sy(final com.android.launcher3.reflection.common.nano.a a, final b b) {
        final List sb = SB(a, b.LL, false);
        sb.add(b);
        a.LI = (b[]) sb.toArray(new b[sb.size()]);
    }

    public static void Sz(final com.android.launcher3.reflection.common.nano.a a, final String s, final List list) {
        final List sb = SB(a, s, false);
        sb.addAll(list);
        a.LI = (b[]) sb.toArray(new b[sb.size()]);
    }
}