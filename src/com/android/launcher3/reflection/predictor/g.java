package com.android.launcher3.reflection.predictor;

import java.util.Arrays;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import com.android.launcher3.reflection.layers.l;
import com.android.launcher3.reflection.a3.k;
import com.android.launcher3.reflection.a3.i;
import com.android.launcher3.reflection.a3.j;
import com.android.launcher3.reflection.a3.f;
import com.android.launcher3.reflection.a3.c;
import com.android.launcher3.reflection.layers.m;
import com.android.launcher3.reflection.layers.o;
import com.android.launcher3.reflection.layers.e;
import com.android.launcher3.reflection.a3.b;
import com.android.launcher3.reflection.a3.a;

public class g extends d implements a
{
    private com.android.launcher3.reflection.layers.a Lu;
    private b Lv;
    private com.android.launcher3.reflection.layers.b Lw;
    private a Lx;

    public g() {
        this.Su(100, Sw());
    }

    private void Su(final int n, final b lv) {
        e.MM = false;
        this.Lw = new com.android.launcher3.reflection.layers.b(1, n);
        (this.Lv = lv).Tc(this);
        (this.Lu = new com.android.launcher3.reflection.layers.a(1)).Tl(new o(false, 1, 1, 1, lv.SW(), n, -1, -1, false, false, 0.0f));
        this.Lu.Tl(new m(1, 1, 2, 1, n, n, -1, -1, false));
    }

    public static c Sw() {
        final c c = new c();
        c.Te(new com.android.launcher3.reflection.a3.d());
        c.Te(new f());
        c.Te(new com.android.launcher3.reflection.a3.e());
        c.Te(new j());
        c.Te(new i());
        c.Te(new com.android.launcher3.reflection.a3.g());
        c.Te(new k());
        return c;
    }

    public com.android.launcher3.reflection.predictor.a Se(final float[] array, final com.android.launcher3.reflection.common.nano.a a) {
        final int n = 1;
        final com.android.launcher3.reflection.layers.b sv = this.Lv.SV(this.Sd().RX().Sm(), a);
        if (this.Sd().RM().size() != n) {
            if (this.Sd().RM().size() > n) {
                while (true) {
                    try {
                        final com.android.launcher3.reflection.layers.b tm = this.Lu.Tm(false, null, sv, true);
                        for (int i = 0; i < array.length; ++i) {
                            array[i] = (float)tm.Tx(false, 0, i);
                        }
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                        final com.android.launcher3.reflection.layers.b tm = null;
                        continue;
                    }
                    break;
                }
            }
        }
        else {
            array[0] = 1.0f;
        }
        final com.android.launcher3.reflection.predictor.a a2 = new com.android.launcher3.reflection.predictor.a();
        a2.RH(sv.MC);
        a2.RF(array);
        return a2;
    }

    public com.android.launcher3.reflection.predictor.a Sf(final com.android.launcher3.reflection.common.nano.a a) {
        final Integer value = this.Sd().RL(a);
        final com.android.launcher3.reflection.predictor.a a2 = new com.android.launcher3.reflection.predictor.a();
        final b lv = this.Lv;
        final com.android.launcher3.reflection.predictor.c sd = this.Sd();
        final com.android.launcher3.reflection.predictor.e rx = sd.RX();
        final com.android.launcher3.reflection.layers.b sv = lv.SV(rx.Sm(), a);
        final com.android.launcher3.reflection.layers.a lu = this.Lu;
        lu.Tt();
        this.Lu.Tm(true, null, sv, true);
        final com.android.launcher3.reflection.layers.b lw = this.Lw;
        final double[] mc = lw.MC;
        l.TU(mc);
        this.Lw.Tz(false, 0, value, 1.0);
        final com.android.launcher3.reflection.layers.a lu2 = this.Lu;
        lu2.Tn(this.Lw, null, 1, true);
        final com.android.launcher3.reflection.layers.a lu3 = this.Lu;
        try {
            lu3.update();
            a2.RH(sv.MC);
            return a2;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public void Sg(final DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeUTF(b.Ta(this.Lv));
        this.Lv.SY(dataOutputStream);
        this.Lu.Tq(dataOutputStream);
    }

    public void Sh(final DataInputStream dataInputStream) throws IOException {
        final String utf = dataInputStream.readUTF();
        final b tb = b.Tb(utf);
        if (tb == null) {
            final String s = "Cannot find extractor with ";
            final String value = String.valueOf(utf);
            String concat;
            if (value.length() == 0) {
                concat = new String(s);
            }
            else {
                concat = s.concat(value);
            }
            throw new IOException(concat);
        }
        tb.SX(dataInputStream);
        tb.Tc(this);
        (this.Lu = new com.android.launcher3.reflection.layers.a(1)).Tr(dataInputStream);
        if (this.Lu.Tp() != tb.SW()) {
            throw new IOException(new StringBuilder(76).append("Model to be loaded has an inconsistent input size:").append(this.Lu.Tp()).append(" != ").append(tb.SW()).toString());
        }
        if (this.Lu.Ts().Uw() == this.Sd().RZ()) {
            return;
        }
        throw new IOException(new StringBuilder(57).append("Inconsistent model output size...").append(this.Lu.Ts().Uw()).append("!=").append(this.Sd().RZ()).toString());
    }

    public void Si(final Integer n, final Integer n2, final String s) {
        final double n3 = 0.0;
        int i = 0;
        final m m = (m)this.Lu.Ts();
        final com.android.launcher3.reflection.layers.b uf = m.Uf(this.Lu);
        final int td = uf.TD(false);
        for (int j = 0; j < uf.TC(false); ++j) {
            int k = 0;
            double n4 = n3;
            while (k < td) {
                n4 += uf.Tx(false, j, k);
                ++k;
            }
            final double n5 = n4 / td;
            if (n != n2) {
                uf.Tz(false, j, n, uf.Tx(false, j, n2));
            }
            uf.Tz(false, j, n2, n5);
        }
        final com.android.launcher3.reflection.layers.b ug = m.Ug();
        double n6 = n3;
        while (i < td) {
            n6 += ug.MC[i];
            ++i;
        }
        final double n7 = n6 / td;
        if (n != n2) {
            ug.MC[n] = ug.MC[n2];
        }
        ug.MC[n2] = n7;
    }

    public void Sj(final String s, final String s2) {
        this.Lv.SZ(Arrays.asList(".*", s));
    }

    public void Sv(final b b, final int n) {
        if (this.Lx != null) {
            this.Lx.Sv(b, n);
        }
        final com.android.launcher3.reflection.layers.b uf = ((com.android.launcher3.reflection.layers.m)this.Lu.Tk().get(0)).Uf(this.Lu);
        final int tc = uf.TC(false);
        for (int i = 0; i < uf.TD(false); ++i) {
            double n2 = 0.0;
            for (int j = 0; j < tc; ++j) {
                n2 += uf.Tx(false, j, i);
            }
            uf.Tz(false, n, i, n2 / tc);
        }
    }

    public g clone() {
        final g g = new g();
        g.Sc(this.Sd());
        g.Lu = this.Lu.clone();
        g.Lw = this.Lw.clone();
        g.Lv = this.Lv.clone();
        return g;
    }
}