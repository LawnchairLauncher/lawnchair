package com.android.launcher3.reflection.layers;

import java.util.ArrayList;
import java.io.DataInputStream;
import java.io.DataOutputStream;

class k implements c
{
    final /* synthetic */ o NE;

    k(final o ne) {
        this.NE = ne;
    }

    public Boolean TH(final int n) {
        final double n2 = 0.1;
        int i = 0;
        for (int j = 0; j < this.NE.Ot; ++j) {
            this.NE.NM.Ty(this.NE.NL, j, n, this.NE.NO.MC[j * this.NE.Ou + n] * -0.1);
        }
        if (this.NE.Ow) {
            while (i < this.NE.Ou) {
                final int n3 = this.NE.Ou * i;
                final double[] mc = this.NE.NN.MC;
                final int n4 = n3 + n;
                mc[n4] -= this.NE.NP.MC[n3 + n] * n2;
                ++i;
            }
        }
        if (!Double.isNaN(this.NE.NR.MC[n])) {
            final double[] mc2 = this.NE.NQ.MC;
            mc2[n] -= this.NE.NR.MC[n] * n2;
            return true;
        }
        throw new RuntimeException("NaN in bias gradients...");
    }
}
