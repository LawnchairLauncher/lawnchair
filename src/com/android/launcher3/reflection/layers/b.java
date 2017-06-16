package com.android.launcher3.reflection.layers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class b
{
    int MA;
    int MB;
    public double[] MC;

    public b() {
    }

    public b(final int ma, final int mb) {
        this.MA = ma;
        this.MB = mb;
        this.MC = new double[ma * mb];
    }

    public static b TE(final b b, final b b2) {
        if (b.TC(false) == b2.TC(false)) {
            final b b3 = new b(b.TC(false), b.TD(false) + b2.TD(false));
            for (int i = 0; i < b3.TC(false); ++i) {
                for (int j = 0; j < b3.TD(false); ++j) {
                    if (j >= b.TD(false)) {
                        b3.Tz(false, i, j, b2.Tx(false, i, j - b.TD(false)));
                    }
                    else {
                        b3.Tz(false, i, j, b.Tx(false, i, j));
                    }
                }
            }
            return b3;
        }
        throw new RuntimeException();
    }

    public static b Tu(final b b, final b b2, final b b3, final boolean b4) {
        if (b.TD(false) == b2.TD(false) && b.TC(false) == b2.TC(false) && b3.TD(false) == b2.TD(false) && b3.TC(false) == b2.TC(false)) {
            e.getInstance().TI(b3.MC.length, new p(b4, b3, b, b2));
            return b3;
        }
        throw new RuntimeException(new StringBuilder(71).append(b.TC(false)).append("x").append(b.TD(false)).append(" ").append(b2.TC(false)).append("x").append(b2.TD(false)).append(" ").append(b3.TC(false)).append("x").append(b3.TD(false)).toString());
    }

    public static b Tv(final b b, final b b2, final boolean b3, final b b4, final boolean b5) {
        if (b.TD(false) == b2.TC(b3) && b4.TC(false) == b.TC(false) && b4.TD(false) == b2.TD(b3)) {
            e.getInstance().TI(b4.MC.length, new h(b5, b4, b, b2, b3));
            return b4;
        }
        throw new RuntimeException(new StringBuilder(71).append(b.TC(false)).append("x").append(b.TD(false)).append(" ").append(b2.TC(b3)).append("x").append(b2.TD(b3)).append(" ").append(b4.TC(false)).append("x").append(b4.TD(false)).toString());
    }

    public void TA(final DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeInt(this.MA);
        dataOutputStream.writeInt(this.MB);
        for (int i = 0; i < this.MC.length; ++i) {
            dataOutputStream.writeDouble(this.MC[i]);
        }
    }

    public void TB(final DataInputStream dataInputStream) throws IOException {
        this.MA = dataInputStream.readInt();
        this.MB = dataInputStream.readInt();
        this.MC = new double[this.MA * this.MB];
        for (int i = 0; i < this.MC.length; ++i) {
            this.MC[i] = dataInputStream.readDouble();
        }
    }

    public int TC(final boolean b) {
        if (!b) {
            return this.MA;
        }
        return this.MB;
    }

    public int TD(final boolean b) {
        if (!b) {
            return this.MB;
        }
        return this.MA;
    }

    public b TF(final double n) {
        e.getInstance().TI(this.MC.length, new q(this, n));
        return this;
    }

    public b TG(final b b) {
        if (this.TD(false) == b.TD(false) && this.TC(false) == b.TC(false)) {
            e.getInstance().TI(this.MC.length, new j(this, b));
            return this;
        }
        throw new RuntimeException(new StringBuilder(47).append(this.TC(false)).append("x").append(this.TD(false)).append(" ").append(b.TC(false)).append("x").append(b.TD(false)).toString());
    }

    public int Tw(final boolean b, final int n, final int n2) {
        if (!b) {
            return this.MB * n + n2;
        }
        return this.MB * n2 + n;
    }

    public double Tx(final boolean b, final int n, final int n2) {
        final int n3 = 41;
        if (n >= this.TC(b)) {
            throw new RuntimeException(new StringBuilder(n3).append("requested row: ").append(n).append(" >= ").append(this.TC(b)).toString());
        }
        if (n2 < this.TD(b)) {
            return this.MC[this.Tw(b, n, n2)];
        }
        throw new RuntimeException(new StringBuilder(n3).append("requested col: ").append(n2).append(" >= ").append(this.TD(b)).toString());
    }

    public void Ty(final boolean b, final int n, final int n2, final double n3) {
        final double[] mc = this.MC;
        final int tw = this.Tw(b, n, n2);
        mc[tw] += n3;
    }

    public void Tz(final boolean b, final int n, final int n2, final double n3) {
        this.MC[this.Tw(b, n, n2)] = n3;
    }

    public void clear() {
        l.TU(this.MC);
    }

    public b clone() {
        final b b = new b(this.MA, this.MB);
        for (int i = 0; i < this.MC.length; ++i) {
            b.MC[i] = this.MC[i];
        }
        return b;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.TC(false); ++i) {
            for (int j = 0; j < this.TD(false); ++j) {
                sb.append(new StringBuilder(25).append(this.Tx(false, i, j)).append(" ").toString());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}