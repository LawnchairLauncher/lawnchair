package com.android.launcher3.reflection.layers;

class p implements c
{
    final /* synthetic */ boolean NV;
    final /* synthetic */ b NW;
    final /* synthetic */ b NX;
    final /* synthetic */ b NY;

    p(final boolean nv, final b nw, final b nx, final b ny) {
        this.NV = nv;
        this.NW = nw;
        this.NX = nx;
        this.NY = ny;
    }

    public Boolean TH(final int n) {
        if (!this.NV) {
            this.NW.MC[n] = this.NX.MC[n] + this.NY.MC[n];
        }
        else {
            final double[] mc = this.NW.MC;
            mc[n] += this.NX.MC[n] + this.NY.MC[n];
        }
        return true;
    }
}
