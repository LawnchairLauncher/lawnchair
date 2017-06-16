package com.android.launcher3.reflection.layers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Iterator;
import com.android.launcher3.reflection.common.d;
import java.util.ArrayList;

class u implements c
{
    final /* synthetic */ ArrayList[] Oi;
    final /* synthetic */ b Oj;
    final /* synthetic */ b Ok;
    final /* synthetic */ o Ol;

    u(final o ol, final ArrayList[] oi, final b oj, final b ok) {
        this.Ol = ol;
        this.Oi = oi;
        this.Oj = oj;
        this.Ok = ok;
    }

    public Boolean TH(final int n) {
        int i = 0;
        final int n2 = n / this.Ol.Ou;
        final int n3 = n % this.Ol.Ou;
        final double n4 = this.Ol.Op.MC[this.Ol.Ou * n2 + n3];
        if (!this.Ol.Os) {
            for (int j = 0; j < this.Ol.Ot; ++j) {
                final double[] mc = this.Ol.NO.MC;
                final int n5 = this.Ol.Ou * j + n3;
                mc[n5] += this.Oj.MC[this.Ol.Ot * n2 + j] * n4;
            }
        }
        else {
            for (final Object o : this.Oi[n2]) {
                d d = (d) o;
                final double[] mc2 = this.Ol.NO.MC;
                final int n6 = d.Mn * this.Ol.Ou + n3;
                mc2[n6] += d.Mo * n4;
            }
        }
        if (this.Ol.Ow && this.Ok != null) {
            while (i < this.Ol.Ou) {
                final double[] mc3 = this.Ol.NP.MC;
                final int n7 = this.Ol.Ou * i + n3;
                mc3[n7] += this.Ok.MC[this.Ol.Ou * n2 + i] * n4;
                ++i;
            }
        }
        final double[] mc4 = this.Ol.NR.MC;
        mc4[n3] += n4;
        return true;
    }
}