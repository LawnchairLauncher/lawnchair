package com.android.launcher3.reflection.layers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Iterator;
import com.android.launcher3.reflection.common.d;
import java.util.ArrayList;

class x implements c
{
    final /* synthetic */ a OC;
    final /* synthetic */ ArrayList[] OD;
    final /* synthetic */ b OE;
    final /* synthetic */ b OF;
    final /* synthetic */ b OG;
    final /* synthetic */ o OH;

    x(final o oh, final a oc, final ArrayList[] od, final b oe, final b of, final b og) {
        this.OH = oh;
        this.OC = oc;
        this.OD = od;
        this.OE = oe;
        this.OF = of;
        this.OG = og;
    }

    public Boolean TH(final int n) {
        final int n2 = n / this.OH.Ou;
        final int n3 = n % this.OH.Ou;
        final b uf = this.OH.Uf(this.OC);
        final int n4 = n2 * this.OH.Ou;
        final double n5 = this.OH.NQ.MC[n3];
        double n6;
        if (!this.OH.Os) {
            n6 = n5;
            for (int i = 0; i < this.OH.Ot; ++i) {
                n6 += this.OE.Tx(false, n2, i) * uf.Tx(this.OH.NL, i, n3);
            }
        }
        else {
            final Iterator iterator = this.OD[n2].iterator();
            n6 = n5;
            while (iterator.hasNext()) {
                final d d = (d) iterator.next();
                n6 += uf.Tx(this.OH.NL, d.Mn, n3) * d.Mo;
            }
        }
        if (this.OH.Ow && this.OF != null) {
            for (int j = 0; j < this.OH.Ou; ++j) {
                n6 += this.OF.Tx(false, n2, j) * this.OH.NN.Tx(false, j, n3);
            }
        }
        this.OG.MC[n4 + n3] = n6;
        return true;
    }
}