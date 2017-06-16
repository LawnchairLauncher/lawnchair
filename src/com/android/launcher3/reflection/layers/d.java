package com.android.launcher3.reflection.layers;

import java.io.DataInputStream;
import java.io.DataOutputStream;

class d implements c
{
    final /* synthetic */ int MD;
    final /* synthetic */ b ME;
    final /* synthetic */ b MF;
    final /* synthetic */ b MG;
    final /* synthetic */ m MH;

    d(final m mh, final int md, final b me, final b mf, final b mg) {
        this.MH = mh;
        this.MD = md;
        this.ME = me;
        this.MF = mf;
        this.MG = mg;
    }

    public Boolean TH(final int n) {
        final boolean b = true;
        if (this.MD != 0) {
            if (this.MD != 2) {
                throw new RuntimeException("unsupported activation function for the output layer");
            }
            this.ME.MC[n] = this.MF.MC[n] - this.ME.MC[n];
        }
        else if (this.MH.NF != 0) {
            if (this.MH.NF == (b ? 1 : 0)) {
                this.ME.MC[n] = this.MF.MC[n] - this.ME.MC[n];
            }
        }
        else {
            this.ME.MC[n] = this.MF.MC[n] * (1.0 - this.MF.MC[n]) * (this.MF.MC[n] - this.ME.MC[n]);
        }
        if (this.MG != null) {
            final double[] mc = this.ME.MC;
            mc[n] *= this.MG.MC[n];
        }
        return b;
    }
}