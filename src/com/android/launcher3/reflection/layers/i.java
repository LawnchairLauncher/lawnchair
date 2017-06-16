package com.android.launcher3.reflection.layers;

import java.util.Iterator;
import com.android.launcher3.reflection.common.d;
import java.util.ArrayList;

public class i
{
    private b NA;
    private b NB;
    private int Nb;
    private int Nc;
    private int Nd;
    private int Ne;
    private com.android.launcher3.reflection.common.b Nf;
    private com.android.launcher3.reflection.common.b Ng;
    private com.android.launcher3.reflection.common.b Nh;
    private com.android.launcher3.reflection.common.b Ni;
    private com.android.launcher3.reflection.common.b Nj;
    private com.android.launcher3.reflection.common.b Nk;
    private b[] Nl;
    private b Nm;
    private b Nn;
    private b No;
    private b Np;
    private b Nq;
    private b Nr;
    private b Ns;
    private b Nt;
    private b Nu;
    private b Nv;
    private b Nw;
    private b Nx;
    private b Ny;
    private b Nz;

    private void TN(final boolean b, final b b2, final int n, final int n2, final b[] array, final ArrayList[] array2, final b b3) {
        TO(b, b2, array[1], 0, array2, b3, n, 0, n2);
        TO(b, b2, array[2], 0, array2, b3, n, 1, n2);
        TO(b, b2, array[4], 0, array2, b3, n, 2, n2);
    }

    private static void TO(final boolean b, final b b2, final b b3, final int n, final ArrayList[] array, final b b4, final int n2, final int n3, final int n4) {
        for (int i = 0; i < n4; ++i) {
            final double tx = b3.Tx(false, i, n);
            if (!b) {
                if (array == null) {
                    if (b4 != null) {
                        for (int j = 0; j < n2; ++j) {
                            b2.Ty(false, j, n3, b4.Tx(false, i, j) * tx);
                        }
                    }
                }
                else {
                    for (final Object o : array[i]) {
                        d d = (d) o;
                        b2.Ty(false, d.Mn, n3, d.Mo * tx);
                    }
                }
            }
            else {
                b2.Ty(false, 0, n3, tx);
            }
        }
    }

    private double TP(final int n, final ArrayList[] array, final b b, final b b2, final b b3, final int n2) {
        final double n3 = 0.0;
        double n4;
        if (array == null) {
            n4 = n3;
            for (int i = 0; i < this.Nd; ++i) {
                n4 += b.Tx(false, n, i) * this.Nm.Tx(false, i, n2);
            }
        }
        else {
            final Iterator<d> iterator = array[n].iterator();
            n4 = n3;
            while (iterator.hasNext()) {
                final d d = iterator.next();
                n4 += this.Nm.Tx(false, d.Mn, n2) * d.Mo;
            }
        }
        for (int n5 = 0; b2 != null && n5 < this.Ne; ++n5) {
            n4 += b2.Tx(false, n, n5) * this.Nn.Tx(false, n5, n2);
        }
        for (int n6 = 0; b3 != null && n6 < this.Nc; ++n6) {
            n4 += b3.Tx(false, n, n6) * this.No.Tx(false, n6, n2);
        }
        return n4;
    }

    public void TL(final ArrayList[] array, final b b, final b b2, final b b3) throws Exception {
        b b4 = (b)this.Ni.SD();
        if (b4 == null) {
            b4 = new b(this.Nb, 1);
        }
        final b b5 = (b)this.Ni.add(b4);
        b b6 = (b)this.Nj.SD();
        if (b6 == null) {
            b6 = new b(this.Nb, 1);
        }
        final b b7 = (b)this.Nj.add(b6);
        b b8 = (b)this.Nk.SD();
        if (b8 == null) {
            b8 = new b(this.Nb, 1);
        }
        final b b9 = (b)this.Nk.add(b8);
        final b b10 = (b)this.Ng.SI();
        b b11 = (b)this.Nf.SD();
        if (b11 == null) {
            b11 = new b(this.Nb, this.Nc);
        }
        final b b12 = (b)this.Nf.add(b11);
        b b13 = (b)this.Ng.SD();
        if (b13 == null) {
            b13 = new b(this.Nb, this.Nc);
        }
        final b b14 = (b)this.Ng.add(b13);
        b b15 = (b)this.Nh.SD();
        if (b15 == null) {
            b15 = new b(this.Nb, this.Nc);
        }
        final b b16 = (b)this.Nh.add(b15);
        for (int i = 0; i < this.Nb; ++i) {
            final double tw = l.TW(this.TP(i, array, b, b2, b10, 0) + this.Np.MC[0]);
            b9.Tz(false, i, 0, tw);
            final double tw2 = l.TW(this.TP(i, array, b, b2, b10, 1) + this.Np.MC[1]);
            b7.Tz(false, i, 0, tw2);
            for (int j = 0; j < this.Nc; ++j) {
                double n = this.Ns.MC[j];
                if (array == null) {
                    for (int k = 0; k < this.Nd; ++k) {
                        n += b.Tx(false, i, k) * this.Nr.Tx(false, k, j);
                    }
                }
                else {
                    for (final Object o : array[i]) {
                        d d = (d) o;
                        n += d.Mo * this.Nr.Tx(false, d.Mn, j);
                    }
                }
                for (int n2 = 0; b2 != null && n2 < this.Ne; ++n2) {
                    n += b2.Tx(false, i, n2) * this.Nq.Tx(false, n2, j);
                }
                final double tanh = Math.tanh(n);
                b16.Tz(false, i, j, tanh);
                double n3 = tw * tanh;
                if (b10 != null) {
                    n3 += b10.Tx(false, i, j) * tw2;
                }
                b14.Tz(false, i, j, n3);
                if (Double.isNaN(n3)) {
                    throw new Exception(new StringBuilder(78).append(tw).append(" x ").append(tanh).append(" + ").append(tw2).toString());
                }
            }
            final double tw3 = l.TW(this.TP(i, array, b, b2, b14, 2) + this.Np.MC[2]);
            b5.Tz(false, i, 0, tw3);
            for (int l = 0; l < this.Nc; ++l) {
                final double tanh2 = Math.tanh(b14.Tx(false, i, l));
                b12.Tz(false, i, l, tanh2);
                b3.Tz(false, i, l, tw3 * tanh2);
                if (Double.isNaN(tw3 * tanh2)) {
                    throw new Exception(new StringBuilder(82).append(tw3).append(" x ").append(tanh2).append("=tanh(").append(b14.Tx(false, i, l)).append(")").toString());
                }
            }
        }
    }

    public void TM(final int n, final ArrayList[] array, final b b, final b b2, final b b3) {
        final b b4 = (b)this.Nf.SG(n);
        final b b5 = new b(this.Nb, 1);
        for (int i = 0; i < this.Nb; ++i) {
            double n2 = 0.0;
            for (int j = 0; j < this.Nc; ++j) {
                n2 += b4.Tx(false, i, j) * b3.Tx(false, i, j);
            }
            final double tx = ((b)this.Ni.SG(n)).Tx(false, i, 0);
            b5.Tz(false, i, 0, n2 * (tx * (1.0 - tx)));
        }
        final b b6 = new b(this.Nb, this.Nc);
        final b b7 = (b)this.Nj.SG(n + 1);
        final b b8 = (b)this.Ng.SG(n);
        for (int k = 0; k < this.Nb; ++k) {
            final double tx2 = ((b)this.Ni.SG(n)).Tx(false, k, 0);
            double tx3;
            if (b7 == null) {
                tx3 = 0.0;
            }
            else {
                tx3 = b7.Tx(false, k, 0);
            }
            for (int l = 0; l < this.Nc; ++l) {
                final double tx4 = b4.Tx(false, k, l);
                double n3 = (1.0 - tx4 * tx4) * tx2 * b3.Tx(false, k, l);
                if (this.Nl[0] != null) {
                    n3 = n3 + this.Nl[0].Tx(false, k, l) * tx3 + this.No.Tx(false, l, 0) * this.Nl[1].Tx(false, k, 0) + this.No.Tx(false, l, 1) * this.Nl[2].Tx(false, k, 0);
                }
                b6.Tz(false, k, l, n3 + this.No.Tx(false, l, 2) * b5.Tx(false, k, 0));
            }
        }
        final b b9 = (b)this.Nk.SG(n);
        final b b10 = (b)this.Nh.SG(n);
        final b b11 = new b(this.Nb, this.Nc);
        for (int n4 = 0; n4 < this.Nb; ++n4) {
            final double tx5 = b9.Tx(false, n4, 0);
            for (int n5 = 0; n5 < this.Nc; ++n5) {
                final double tx6 = b10.Tx(false, n4, n5);
                b11.Tz(false, n4, n5, (1.0 - tx6 * tx6) * tx5 * b6.Tx(false, n4, n5));
            }
        }
        final b b12 = new b(this.Nb, 1);
        final b b13 = (b)this.Nj.SG(n);
        final b b14 = (b)this.Ng.SG(n - 1);
        for (int n6 = 0; n6 < this.Nb; ++n6) {
            final double tx7 = b13.Tx(false, n6, 0);
            final double n7 = (1.0 - tx7) * tx7;
            double n8 = 0.0;
            for (int n9 = 0; b14 != null && n9 < this.Nc; ++n9) {
                n8 += b14.Tx(false, n6, n9) * b6.Tx(false, n6, n9);
            }
            b12.Tz(false, n6, 0, n8 * n7);
        }
        final b b15 = new b(this.Nb, 1);
        final b b16 = (b)this.Nk.SG(n);
        for (int n10 = 0; n10 < this.Nb; ++n10) {
            final double tx8 = b16.Tx(false, n10, 0);
            final double n11 = (1.0 - tx8) * tx8;
            double n12 = 0.0;
            for (int n13 = 0; n13 < this.Nc; ++n13) {
                n12 += b10.Tx(false, n10, n13) * b6.Tx(false, n10, n13);
            }
            b15.Tz(false, n10, 0, n12 * n11);
        }
        this.Nl = new b[] { b6, b15, b12, b11, b5 };
        final b te = b.TE(b.TE(b.TE(b15, b12), b5), b11);
        b.Tv(te, b.TE(this.Nm, this.Nr), true, this.NA, false);
        b.Tv(te, b.TE(this.Nn, this.Nq), true, this.NB, false);
        this.TN(false, this.Nt, this.Nd, this.Nb, this.Nl, array, b);
        this.TN(false, this.Nu, this.Ne, this.Nb, this.Nl, null, b2);
        TO(false, this.Nv, this.Nl[1], 0, null, b14, this.Nc, 0, this.Nb);
        TO(false, this.Nv, this.Nl[2], 0, null, b14, this.Nc, 1, this.Nb);
        TO(false, this.Nv, this.Nl[4], 0, null, b8, this.Nc, 2, this.Nb);
        this.TN(true, this.Nw, 1, this.Nb, this.Nl, null, null);
        for (int n14 = 0; n14 < this.Nc; ++n14) {
            TO(false, this.Ny, this.Nl[3], n14, array, b, this.Nd, n14, this.Nb);
            TO(false, this.Nx, this.Nl[3], n14, null, b2, this.Ne, n14, this.Nb);
            TO(true, this.Nz, this.Nl[3], n14, null, null, 1, n14, this.Nb);
        }
    }

    public void TQ() {
        this.Nl = new b[5];
        l.TU(this.Nz.MC);
        l.TU(this.Nx.MC);
        l.TU(this.Ny.MC);
        l.TU(this.Nv.MC);
        l.TU(this.Nu.MC);
        l.TU(this.Nt.MC);
        l.TU(this.Nw.MC);
    }

    b TR() {
        return this.NA;
    }

    b TS() {
        return this.NB;
    }

    public void TT() {
        this.Ng.clear();
        this.Nf.clear();
        this.Nh.clear();
        this.Ni.clear();
        this.Nj.clear();
        this.Nk.clear();
    }

    void update() {
        final double n = -0.1;
        this.Nm.TG(this.Nt.TF(n));
        this.Nn.TG(this.Nu.TF(n));
        this.No.TG(this.Nv.TF(n));
        this.Np.TG(this.Nw.TF(n));
        this.Nq.TG(this.Nx.TF(n));
        this.Nr.TG(this.Ny.TF(n));
        this.Ns.TG(this.Nz.TF(n));
    }
}