package com.android.launcher3.reflection.layers;

import java.io.IOException;
import java.util.ArrayList;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class o extends v
{
    int NK;
    public boolean NL;
    public b NM;
    public b NN;
    public b NO;
    public b NP;
    public b NQ;
    public b NR;
    public int NS;
    public int NT;
    public float NU;

    public o() {
    }

    public o(final boolean b, final int n, final int nk, final int n2, final int n3, final int n4, final int ns, final int nt, final boolean nl, final boolean b2, final float nu) {
        super(b, n, n2, n3, n4);
        this.NS = ns;
        this.NU = nu;
        this.NQ = new b(1, n4);
        this.NK = nk;
        this.NO = new b(n3, n4);
        this.NP = new b(n4, n4);
        this.NR = new b(1, n4);
        this.NL = nl;
        if (ns < 0) {
            l.TZ(this.NM = new b(n3, n4), b2);
            l.TU(this.NQ.MC);
        }
        this.NT = nt;
        l.TZ(this.NN = new b(n4, n4), b2);
    }

    private static void Uk(final int n, final b b, final b b2) {
        if (n != 1 && n != 0) {
            if (n != 2) {
                throw new RuntimeException(new StringBuilder(44).append("Unsupported activation function: ").append(n).toString());
            }
            e.getInstance().TI(b.TC(false), new s(b, b2));
        }
        else {
            e.getInstance().TI(b.MC.length, new f(n, b2, b));
        }
    }

    void Ua(final int n, final b b, final b b2, final b b3) {
        e.getInstance().TI(b.MC.length, new t(this, n, b2, b));
    }

    public void Ub(final DataOutputStream dataOutputStream) throws IOException {
        super.Ub(dataOutputStream);
        dataOutputStream.writeInt(this.NK);
        dataOutputStream.writeBoolean(this.NL);
        this.NN.TA(dataOutputStream);
        this.NQ.TA(dataOutputStream);
        dataOutputStream.writeInt(this.NS);
        if (this.NS < 0) {
            this.NM.TA(dataOutputStream);
        }
        dataOutputStream.writeInt(this.NT);
        this.UA(dataOutputStream);
    }

    public void Uc(final DataInputStream dataInputStream) throws IOException {
        super.Uc(dataInputStream);
        this.NK = dataInputStream.readInt();
        this.NL = dataInputStream.readBoolean();
        (this.NN = new b()).TB(dataInputStream);
        (this.NQ = new b()).TB(dataInputStream);
        this.NS = dataInputStream.readInt();
        if (this.NS < 0) {
            (this.NM = new b()).TB(dataInputStream);
        }
        this.NO = new b(this.Ot, this.Ou);
        this.NP = new b(this.Ou, this.Ou);
        this.NR = new b(1, this.Ou);
        this.NT = dataInputStream.readInt();
        this.UB(dataInputStream);
    }

    void Ue() {
        super.Ue();
        l.TU(this.NO.MC);
        l.TU(this.NP.MC);
        l.TU(this.NR.MC);
    }

    public b Uf(final a a) {
        if (this.NS < 0) {
            return this.NM;
        }
        return ((o)a.Tk().get(this.NS)).NM;
    }

    public b Ug() {
        return this.NQ;
    }

    public void Uh(final o o) {
        super.Uu(o);
        o.NL = this.NL;
        o.NK = this.NK;
        o.NM = this.NM.clone();
        o.NO = this.NO.clone();
        o.NN = this.NN.clone();
        o.NP = this.NP.clone();
        o.NQ = this.NQ.clone();
        o.NR = this.NR.clone();
        o.NS = this.NS;
        o.NT = this.NT;
    }

    public void Ui(final a a, final int n, final b b, final b b2, final b b3) {
        final boolean b4 = true;
        b.Tu(b, b2, this.Op, false);
        this.Ua(this.NK, this.Op, (b)this.Om.SG(n), b3);
        b.Tv(this.Op, this.Uf(a), !this.NL && b4, this.Oq, false);
        if (this.Ow) {
            b.Tv(this.Op, this.NN, b4, this.Or, false);
        }
        e.getInstance().TI(this.Ov * this.Ou, new u(this, (ArrayList[])this.Oo.SG(n), (b)this.On.SG(n), (b)this.Om.SG(n - 1)));
    }

    public b Uj(final boolean b, final a a, final ArrayList[] array, final b b2) {
        b b3 = null;
        final int os = 1;
        int i = 0;
        if (array == null) {
            this.Os = false;
            this.On.add(b2);
        }
        else {
            this.Os = (os != 0);
            this.Oo.add(array);
        }
        if (this.Om.SH() > os) {
            b3 = (b)this.Om.SI();
        }
        final b b4 = new b(this.Ov, this.Ou);
        e.getInstance().TI(this.Ov * this.Ou, new x(this, a, array, b2, b3, b4));
        final b b5 = (b)this.Om.add(new b(this.Ov, this.Ou));
        Uk(this.NK, b4, b5);
        if (this.NU > 0.0f) {
            if (!b) {
                while (i < b5.MC.length) {
                    final double[] mc = b5.MC;
                    mc[i] *= 1.0f - this.NU;
                    ++i;
                }
            }
            else {
                for (int j = 0; j < b5.MC.length; ++j) {
                    if (Math.random() < this.NU) {
                        b5.MC[j] = 0.0;
                    }
                }
            }
        }
        return b5;
    }

    public int Ul() {
        return this.NS;
    }

    public o clone() {
        final o o = new o();
        super.Uu(o);
        o.NL = this.NL;
        o.NK = this.NK;
        o.NM = this.NM.clone();
        o.NO = this.NO.clone();
        o.NN = this.NN.clone();
        o.NP = this.NP.clone();
        o.NQ = this.NQ.clone();
        o.NR = this.NR.clone();
        o.NS = this.NS;
        o.NT = this.NT;
        return o;
    }

    public String getName() {
        return "LinearLayer";
    }

    public void update() {
        e.getInstance().TI(this.Ou, new k(this));
    }
}
