package com.android.launcher3.reflection.layers;

import java.util.ArrayList;

class g implements c {
    final /* synthetic */ ArrayList[] MR;
    final /* synthetic */ b MS;
    final /* synthetic */ b MT;
    final /* synthetic */ b MU;
    final /* synthetic */ r MV;

    g(final r mv, final ArrayList[] mr, final b ms, final b mt, final b mu) {
        this.MV = mv;
        this.MR = mr;
        this.MS = ms;
        this.MT = mt;
        this.MU = mu;
    }

    public Boolean TH(final int n) throws Exception {
        final b b = new b(this.MV.Ov, this.MV.cellSize);
        this.MV.Ob[n].TL(this.MR, this.MS, this.MT, b);
        final int n2 = n * this.MV.cellSize;
        for (int i = 0; i < this.MV.Ov; ++i) {
            for (int j = 0; j < this.MV.cellSize; ++j) {
                this.MU.Tz(false, i, n2 + j, b.Tx(false, i, j));
            }
        }
        return true;
    }
}