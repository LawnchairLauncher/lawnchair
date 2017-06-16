package com.android.launcher3.reflection.layers;

class q implements c
{
    final /* synthetic */ double NZ;
    final /* synthetic */ b Oa;

    q(final b oa, final double nz) {
        this.Oa = oa;
        this.NZ = nz;
    }

    public Boolean TH(final int n) {
        if (this.Oa.MC[n] != 0.0) {
            final double[] mc = this.Oa.MC;
            mc[n] *= this.NZ;
        }
        return true;
    }
}
