package com.android.launcher3.reflection.layers;

import java.util.ArrayList;

class w implements c
{
    final /* synthetic */ b OA;
    final /* synthetic */ r OB;
    final /* synthetic */ int Ox;
    final /* synthetic */ ArrayList[] Oy;
    final /* synthetic */ b Oz;

    w(final r ob, final int ox, final ArrayList[] oy, final b oz, final b oa) {
        this.OB = ob;
        this.Ox = ox;
        this.Oy = oy;
        this.Oz = oz;
        this.OA = oa;
    }

    public Boolean TH(final int n) {
        final b b = new b(this.OB.Ov, this.OB.cellSize);
        final int n2 = n * this.OB.cellSize;
        for (int i = 0; i < this.OB.Ov; ++i) {
            for (int j = 0; j < this.OB.cellSize; ++j) {
                b.Tz(false, i, j, this.OB.Op.Tx(false, i, n2 + j));
            }
        }
        this.OB.Ob[n].TM(this.Ox, this.Oy, this.Oz, this.OA, b);
        return true;
    }
}
