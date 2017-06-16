package com.android.launcher3.reflection.a3;

import java.io.IOException;
import java.util.Collection;
import com.android.launcher3.reflection.common.e;
import java.util.Arrays;
import java.util.List;
import java.io.DataOutputStream;
import com.android.launcher3.reflection.common.c;
import java.io.DataInputStream;
import java.util.ArrayList;
import com.android.launcher3.reflection.common.nano.a;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

public class d extends b
{
    protected HashMap<String, Integer> Ms;
    protected HashMap<Integer, Long> Mt;
    protected boolean[] Mu;
    protected int Mv;
    protected int Mw;

    public d() {
        this.Ms = new HashMap();
        this.Mt = new HashMap();
        this.Mv = 200;
        this.Mw = 2;
        this.Mu = new boolean[this.Mv];
    }

    public d(final int mv) {
        this.Ms = new HashMap();
        this.Mt = new HashMap();
        this.Mv = 200;
        this.Mw = 2;
        this.Mv = mv;
        this.Mu = new boolean[this.Mv];
    }

    private String Tg() {
        final long n = Long.MAX_VALUE;
        final Iterator<Map.Entry<String, Integer>> iterator = this.Ms.entrySet().iterator();
        long n2 = n;
        String s = null;
        while (iterator.hasNext()) {
            final Map.Entry<String, Integer> entry = iterator.next();
            final long longValue = this.Mt.get(entry.getValue());
            int n3;
            if (longValue >= n2) {
                n3 = 1;
            }
            else {
                n3 = 0;
            }
            String s2;
            long n4;
            if (n3 == 0) {
                s2 = (String)entry.getKey();
                n4 = longValue;
            }
            else {
                s2 = s;
                n4 = n2;
            }
            n2 = n4;
            s = s2;
        }
        return s;
    }

    public com.android.launcher3.reflection.layers.b SV(final com.android.launcher3.reflection.common.b b, final a a) {
        final ArrayList ti = this.Ti(b, a, 600000L, this.Mw);
        final com.android.launcher3.reflection.layers.b b2 = new com.android.launcher3.reflection.layers.b(1, this.Mv);
        for (final Object o : ti) {
            com.android.launcher3.reflection.common.d d = (com.android.launcher3.reflection.common.d) o;
            if (d.Mo > 0.0f) {
                if (d.Mn >= this.Mv) {
                    throw new RuntimeException(new StringBuilder(26).append("invalid index: ").append(d.Mn).toString());
                }
                b2.MC[d.Mn] = 1.0;
            }
        }
        return b2;
    }

    public int SW() {
        return this.Mv;
    }

    public void SX(final DataInputStream dataInputStream) throws IOException {
        this.Mv = dataInputStream.readInt();
        this.Ms = c.SM(dataInputStream, String.class, Integer.class);
        this.Mt = c.SM(dataInputStream, Integer.class, Long.class);
        this.Mu = new boolean[this.Mv];
        final Iterator<Integer> iterator = this.Ms.values().iterator();
        while (iterator.hasNext()) {
            this.Mu[iterator.next()] = true;
        }
    }

    public void SY(final DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeInt(this.Mv);
        c.SK(dataOutputStream, this.Ms);
        c.SK(dataOutputStream, this.Mt);
    }

    public void SZ(final List list) {
        if (!list.isEmpty()) {
            final Integer n = this.Ms.remove(list.get(0));
            if (n != null) {
                this.Mt.remove(n);
                this.Mu[n] = false;
                this.Td(n);
            }
        }
    }

    protected int Th(final String s, final long n) {
        final int n2 = 1;
        int i = 0;
        Integer value = this.Ms.get(s);
        if (value == null) {
            if (this.Ms.size() != this.Mv) {
                while (i < this.Mu.length) {
                    if (!this.Mu[i]) {
                        value = i;
                        this.Mu[i] = (n2 != 0);
                        break;
                    }
                    ++i;
                }
            }
            else {
                final String tg = this.Tg();
                value = (Integer)this.Ms.get(tg);
                final String[] array = new String[n2];
                array[0] = tg;
                this.SZ(Arrays.asList(array));
                this.Mu[value] = (n2 != 0);
            }
            this.Ms.put(s, value);
        }
        this.Mt.put(value, n);
        return value;
    }

    protected ArrayList Ti(final com.android.launcher3.reflection.common.b b, final a a, final long n, final int n2) {
        final ArrayList list = new ArrayList();
        final HashMap<Object, com.android.launcher3.reflection.common.d> hashMap = new HashMap<Object, com.android.launcher3.reflection.common.d>();
        Label_0037:
        for (int i = b.SF() - 1; i >= 0; --i) {
            a a2;
            Object o;
            long sr;
            int n3;
            int th = 0;
            Label_0158_Outer:
            while (true) {
                o = (a2 = (a)b.SG(i));
                while (true) {
                    Label_0218:
                    while (true) {
                        try {
                            sr = e.SR(a2, a);
                            if (sr - ((a)o).LH <= n) {
                                n3 = 1;
                                if (n3 == 0) {
                                    break Label_0037;
                                }
                                o = ((a)o).Ly;
                                th = this.Th((String)o, a.LC);
                                o = th;
                                o = hashMap.get(o);
                                if (o != null) {
                                    ++((com.android.launcher3.reflection.common.d)o).Mo;
                                    break;
                                }
                                break Label_0218;
                            }
                        }
                        catch (Exception ex) {
                            sr = Long.MAX_VALUE;
                            continue Label_0158_Outer;
                        }
                        n3 = 0;
                        continue Label_0158_Outer;
                    }
                    if (hashMap.size() < n2) {
                        o = new com.android.launcher3.reflection.common.d(th);
                        hashMap.put(th, (com.android.launcher3.reflection.common.d)o);
                        continue;
                    }
                    break;
                }
                break Label_0037;
            }
        }
        list.addAll(hashMap.values());
        return list;
    }

    public d clone() {
        final d d = new d(this.Mv);
        for (final Map.Entry<String, Integer> entry : this.Ms.entrySet()) {
            d.Ms.put(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<Integer, Long> entry2 : this.Mt.entrySet()) {
            d.Mt.put(entry2.getKey(), entry2.getValue());
        }
        d.Mu = Arrays.copyOf(this.Mu, this.Mu.length);
        d.Tc(this.Mp);
        return d;
    }
}