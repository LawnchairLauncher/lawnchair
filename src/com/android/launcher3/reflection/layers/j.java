package com.android.launcher3.reflection.layers;

class j implements c
{
    final /* synthetic */ b NC;
    final /* synthetic */ b ND;

    j(final b nd, final b nc) {
        this.ND = nd;
        this.NC = nc;
    }

    public Boolean TH(final int n) {
        if (this.NC.MC[n] != 0.0) {
            final double[] mc = this.ND.MC;
            mc[n] += this.NC.MC[n];
        }
        return true;
    }
}
