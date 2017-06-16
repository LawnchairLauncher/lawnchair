package com.android.launcher3.reflection.layers;

class h implements c
{
    final /* synthetic */ boolean MW;
    final /* synthetic */ b MX;
    final /* synthetic */ b MY;
    final /* synthetic */ b MZ;
    final /* synthetic */ boolean Na;

    h(final boolean mw, final b mx, final b my, final b mz, final boolean na) {
        this.MW = mw;
        this.MX = mx;
        this.MY = my;
        this.MZ = mz;
        this.Na = na;
    }

    private double TK(final int n, final int n2) {
        final int td = this.MY.TD(false);
        double n3 = 0.0;
        for (int i = 0; i < td; ++i) {
            n3 += this.MY.Tx(false, n, i) * this.MZ.Tx(this.Na, i, n2);
        }
        return n3;
    }

    public Boolean TH(final int n) {
        if (!this.MW) {
            this.MX.MC[n] = this.TK(n / this.MX.TD(false), n % this.MX.TD(false));
        }
        else {
            final double[] mc = this.MX.MC;
            mc[n] += this.TK(n / this.MX.TD(false), n % this.MX.TD(false));
        }
        return true;
    }
}
