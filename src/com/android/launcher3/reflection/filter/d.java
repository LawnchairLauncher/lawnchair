package com.android.launcher3.reflection.filter;

import android.content.ComponentName;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.LinkedList;
import com.android.launcher3.reflection.a2.b;
import com.android.launcher3.reflection.b_research;

import java.util.HashSet;

public class d
{
    private static long p;
    private static int q;
    private static int r;
    private final HashSet s;
    private final b t;
    private final LinkedList u;

    static {
        d.r = 1;
        d.p = 21600000L;
        d.q = 10;
    }

    public d(final b t) {
        this.u = new LinkedList();
        this.s = new HashSet();
        this.t = t;
    }

    private void p() {
        final long timeInMillis = Calendar.getInstance().getTimeInMillis();
        while (this.u.size() > 0 && (timeInMillis > ((e)this.u.peek()).x + d.p || ((e)this.u.peek()).v > d.q)) {
            this.u.removeFirst();
        }
    }

    public void c(final List list, final List list2) {
        float n = 1.0f;
        this.p();
        if (list.size() > 0) {
            n += ((b_research) list.get(0)).Le;
        }
        this.s.clear();
        final Iterator<b_research> iterator = list.iterator();
        while (iterator.hasNext()) {
            this.s.add(iterator.next().Ld);
        }
        final ArrayList<String> list3 = new ArrayList<>();
        for (Object o : this.u) {
            e e = (e) o;
            if (!this.s.contains(e.w)) {
                list3.add(e.w);
            }
        }
        int i = Math.max(list3.size() - d.r, 0);
        int n2 = 0;
        while (i < list3.size()) {
            final b_research b = new b_research(list3.get(i), n2 + n);
            list.add(0, b);
            if (list2 != null) {
                list2.add(0, b);
            }
            ++n2;
            ++i;
        }
    }

    public void o(final ComponentName componentName, final long n, final long n2) {
        this.u.add(new e(this, componentName, n, n2));
        this.p();
    }

    public void q() {
        for (Object o : this.u) {
            e e = (e) o;
            ++e.v;
        }
        this.p();
    }

    void setMaxNumPromotion(final int r) {
        d.r = r;
    }

    class e {
        public int v;
        public String w;
        public long x;
        final /* synthetic */ d y;

        public e(final d y, final ComponentName componentName, final long n, final long x) {
            this.y = y;
            this.w = y.t.y(componentName, n);
            this.x = x;
            this.v = 0;
        }
    }
}

