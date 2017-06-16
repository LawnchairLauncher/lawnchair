package com.android.launcher3.reflection.predictor;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import com.android.launcher3.reflection.common.nano.a;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

public class c
{
    private HashMap<Integer, Integer> Lf;
    private HashMap<String, Integer> Lg;
    private d Lh;
    private int Li;
    private HashMap Lj;
    private HashMap<Integer, Long> Lk;
    private float[] Ll;
    private e Lm;
    private int Ln;
    private f Lo;

    public c() {
        this.Lf = new HashMap();
        this.Lg = new HashMap();
        this.Lj = new HashMap();
        this.Lk = new HashMap();
        this.Ll = null;
        this.Ln = 100;
    }

    public c(final d lh) {
        this.Lf = new HashMap();
        this.Lg = new HashMap();
        this.Lj = new HashMap();
        this.Lk = new HashMap();
        this.Ll = null;
        this.Ln = 100;
        (this.Lh = lh).Sc(this);
    }

    private String RO() {
        final long n = Long.MAX_VALUE;
        final Iterator<Map.Entry<Integer, Long>> iterator = this.Lk.entrySet().iterator();
        Integer n2 = null;
        long longValue = n;
        while (iterator.hasNext()) {
            final Map.Entry<Integer, Long> entry = iterator.next();
            int n3;
            if (entry.getValue() >= longValue) {
                n3 = 1;
            }
            else {
                n3 = 0;
            }
            Integer n5;
            if (n3 == 0) {
                final Integer n4 = (Integer)entry.getKey();
                longValue = entry.getValue();
                n5 = n4;
            }
            else {
                n5 = n2;
            }
            n2 = n5;
        }
        for (final Map.Entry<String, Integer> entry2 : this.Lg.entrySet()) {
            if (entry2.getValue() == n2) {
                return (String)entry2.getKey();
            }
        }
        return null;
    }

    private void RQ(final String s, final String s2) {
        if (!this.Lg.isEmpty()) {
            final Integer n = this.Lg.remove(s);
            if (n != null) {
                this.Lg.put(s2, n);
            }
            this.Lh.Sj(s, s2);
        }
    }

    private void RS(final String s) {
        if (this.Lg.isEmpty()) {
            return;
        }
        final int n = this.Lg.size() - 1;
        final Integer value = (Integer) this.Lg.remove(s);
        if (!this.Lg.isEmpty()) {
            if (value == null) {
                this.Lh.Si(null, null, s);
                if (this.Lo != null) {
                    this.Lo.St(value, n, s);
                }
            }
            else {
                this.Li -= (int)this.Lf.remove(value);
                this.Lj.remove(value);
                this.Lk.remove(value);
                if (n > value) {
                    for (final Map.Entry<String, Integer> entry : this.Lg.entrySet()) {
                        if (entry.getValue() == n) {
                            entry.setValue(value);
                            break;
                        }
                    }
                    this.Lf.put(value, (int)this.Lf.remove(n));
                    this.Lj.put(value, (long)this.Lj.remove(n));
                    this.Lk.put(value, (long)this.Lk.remove(n));
                }
                this.Lh.Si(value, n, s);
                if (this.Lo != null) {
                    this.Lo.St(value, n, s);
                }
            }
            return;
        }
        Log.e("reflectionPredictorG", "Predictor becomes invalid");
    }

    public static void RV(final com.android.launcher3.reflection.common.nano.d d, final HashMap hashMap, final e e) throws IOException {
        hashMap.clear();
        final com.android.launcher3.reflection.common.nano.c[] md = d.Md;
        for (int length = md.length, i = 0; i < length; ++i) {
            final com.android.launcher3.reflection.common.nano.c c = md[i];
            final c c2 = new c();
            c2.RY(e);
            c2.RT(c);
            hashMap.put(c.LR, c2);
        }
    }

    public static com.android.launcher3.reflection.common.nano.d RW(final HashMap hashMap) throws IOException {
        final com.android.launcher3.reflection.common.nano.d d = new com.android.launcher3.reflection.common.nano.d();
        d.Ma = Calendar.getInstance().getTimeInMillis();
        d.Mb = -1;
        d.Mc = "";
        d.Md = new com.android.launcher3.reflection.common.nano.c[hashMap.size()];
        final Iterator<Map.Entry<String, c>> iterator = hashMap.entrySet().iterator();
        int n = 0;
        while (iterator.hasNext()) {
            final Map.Entry<String, c> entry = iterator.next();
            d.Md[n] = entry.getValue().RU();
            d.Md[n].LR = (String)entry.getKey();
            ++n;
        }
        return d;
    }

    public int RL(final a a) {
        final String ly = a.Ly;
        final long lc = a.LC;
        Integer value = this.Lg.get(ly);
        if (value == null) {
            value = this.Lg.size();
            this.Lg.put(ly, value);
            this.Lj.put(value, lc);
        }
        return value;
    }

    public HashMap RM() {
        return this.Lg;
    }

    public com.android.launcher3.reflection.predictor.a RN(final a a) {
        if (!this.Lg.containsKey(a.Ly) && this.RM().size() == this.RZ()) {
            final String ro = this.RO();
            if (ro != null) {
                try {
                    this.RS(ro);
                }
                catch (Exception ex) {}
            }
        }
        final com.android.launcher3.reflection.predictor.a sf = this.Lh.Sf(a);
        final int rl = this.RL(a);
        Integer value = this.Lf.get(rl);
        if (value == null) {
            value = 0;
        }
        this.Lf.put(rl, value + 1);
        ++this.Li;
        this.Lk.put(rl, a.LC);
        return sf;
    }

    public com.android.launcher3.reflection.predictor.a RP(final a a) {
        if (this.Ll == null || this.Lg.size() > this.Ll.length) {
            this.Ll = new float[this.Lg.size()];
        }
        if (this.Ll.length > 0) {
            Arrays.fill(this.Ll, 0.0f);
        }
        final com.android.launcher3.reflection.predictor.a se = this.Lh.Se(this.Ll, a);
        final ArrayList<Object> list = new ArrayList<Object>(this.Lg.size());
        final float[] re = se.RE();
        for (final Map.Entry<String, Integer> entry : this.Lg.entrySet()) {
            list.add(new b(entry.getKey(), re[(int)entry.getValue()]));
        }
        Collections.sort(list, Collections.reverseOrder());
        se.RJ(list);
        return se;
    }

    public void RR(final String s, final String s2, final Map map) {
        int n = 0;
        final ArrayList<String> list = new ArrayList<String>();
        for (final String s3 : this.Lg.keySet()) {
            if (s3.startsWith(s)) {
                list.add(s3);
            }
        }
        for (final String s4 : list) {
            while (this.Lg.containsKey(new StringBuilder(String.valueOf(s2).length() + 11).append(s2).append(n).toString())) {
                ++n;
            }
            final String string = new StringBuilder(String.valueOf(s2).length() + 11).append(s2).append(n).toString();
            map.put(s4, string);
            this.RQ(s4, string);
        }
    }

    public void RT(final com.android.launcher3.reflection.common.nano.c c) throws IOException {
        int i = 0;
        this.Li = c.LV;
        this.Ln = c.LU;
        this.Lh = d.Sb(c.LS);
        if (this.Lh != null) {
            this.Lh.Sc(this);
            final DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(c.LT));
            this.Lh.Sh(dataInputStream);
            dataInputStream.close();
            this.Lg.clear();
            final com.android.launcher3.reflection.common.nano.f[] lw = c.LW;
            for (int length = lw.length, j = 0; j < length; ++j) {
                final com.android.launcher3.reflection.common.nano.f f = lw[j];
                this.Lg.put(f.name, f.Mh);
            }
            this.Lf.clear();
            final com.android.launcher3.reflection.common.nano.e[] lx = c.LX;
            for (int length2 = lx.length, k = 0; k < length2; ++k) {
                final com.android.launcher3.reflection.common.nano.e e = lx[k];
                this.Lf.put(e.key, (int)e.Mf);
            }
            this.Lj.clear();
            final com.android.launcher3.reflection.common.nano.e[] ly = c.LY;
            for (int length3 = ly.length, l = 0; l < length3; ++l) {
                final com.android.launcher3.reflection.common.nano.e e2 = ly[l];
                this.Lj.put(e2.key, e2.Mf);
            }
            this.Lk.clear();
            for (com.android.launcher3.reflection.common.nano.e[] lz = c.LZ; i < lz.length; ++i) {
                final com.android.launcher3.reflection.common.nano.e e3 = lz[i];
                this.Lk.put(e3.key, e3.Mf);
            }
            return;
        }
        final String s = "Cannot find predictor with ";
        final String value = String.valueOf(c.LS);
        String concat;
        if (value.length() == 0) {
            concat = new String(s);
        }
        else {
            concat = s.concat(value);
        }
        throw new IOException(concat);
    }

    public com.android.launcher3.reflection.common.nano.c RU() throws IOException {
        int n = 0;
        final com.android.launcher3.reflection.common.nano.c c = new com.android.launcher3.reflection.common.nano.c();
        c.LV = this.Li;
        c.LU = this.Ln;
        c.LS = d.Sa(this.Lh);
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        this.Lh.Sg(dataOutputStream);
        dataOutputStream.flush();
        c.LT = byteArrayOutputStream.toByteArray();
        dataOutputStream.close();
        c.LW = new com.android.launcher3.reflection.common.nano.f[this.Lg.size()];
        final Iterator<Map.Entry<String, Integer>> iterator = this.Lg.entrySet().iterator();
        int n2 = 0;
        while (iterator.hasNext()) {
            final Map.Entry<String, Integer> entry = iterator.next();
            final com.android.launcher3.reflection.common.nano.f f = new com.android.launcher3.reflection.common.nano.f();
            f.name = entry.getKey();
            f.Mh = entry.getValue();
            c.LW[n2] = f;
            ++n2;
        }
        c.LX = new com.android.launcher3.reflection.common.nano.e[this.Lf.size()];
        final Iterator<Map.Entry<Integer, Integer>> iterator2 = this.Lf.entrySet().iterator();
        int n3 = 0;
        while (iterator2.hasNext()) {
            final Map.Entry<Integer, Integer> entry2 = iterator2.next();
            final com.android.launcher3.reflection.common.nano.e e = new com.android.launcher3.reflection.common.nano.e();
            e.key = entry2.getKey();
            e.Mf = entry2.getValue();
            c.LX[n3] = e;
            ++n3;
        }
        c.LY = new com.android.launcher3.reflection.common.nano.e[this.Lj.size()];
        final Iterator<Map.Entry<Integer, Long>> iterator3 = this.Lj.entrySet().iterator();
        int n4 = 0;
        while (iterator3.hasNext()) {
            final Map.Entry<Integer, Long> entry3 = iterator3.next();
            final com.android.launcher3.reflection.common.nano.e e2 = new com.android.launcher3.reflection.common.nano.e();
            e2.key = entry3.getKey();
            e2.Mf = entry3.getValue();
            c.LY[n4] = e2;
            ++n4;
        }
        c.LZ = new com.android.launcher3.reflection.common.nano.e[this.Lk.size()];
        for (final Map.Entry<Integer, Long> entry4 : this.Lk.entrySet()) {
            final com.android.launcher3.reflection.common.nano.e e3 = new com.android.launcher3.reflection.common.nano.e();
            e3.key = entry4.getKey();
            e3.Mf = entry4.getValue();
            c.LZ[n] = e3;
            ++n;
        }
        return c;
    }

    public e RX() {
        return this.Lm;
    }

    public void RY(final e lm) {
        this.Lm = lm;
    }

    public int RZ() {
        return this.Ln;
    }
}
