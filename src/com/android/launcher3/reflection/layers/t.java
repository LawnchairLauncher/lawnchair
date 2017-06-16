package com.android.launcher3.reflection.layers;

class t implements c
{
    final /* synthetic */ int Oe;
    final /* synthetic */ b Of;
    final /* synthetic */ b Og;
    final /* synthetic */ o Oh;

    t(final o oh, final int oe, final b of, final b og) {
        this.Oh = oh;
        this.Oe = oe;
        this.Of = of;
        this.Og = og;
    }

    public Boolean TH(final int n) {
        final boolean b = true;
        final double n2 = 0.0;
        if (this.Oe != (b ? 1 : 0)) {
            if (this.Oe != 0) {
                throw new RuntimeException(new StringBuilder(44).append("Unsupported activation function: ").append(this.Oe).toString());
            }
            this.Og.MC[n] *= this.Of.MC[n] * (1.0 - this.Of.MC[n]);
        }
        else if (this.Of.MC[n] == n2) {
            this.Og.MC[n] = n2;
        }
        return b;
    }
}
