package com.android.launcher3.reflection.layers;

import java.util.concurrent.Callable;

class n implements Callable
{
    private int NG;
    int NH;
    int NI;
    c NJ;

    public n(final int ng, final int nh, final int ni, final c nj) {
        this.NG = ng;
        this.NH = nh;
        this.NI = ni;
        this.NJ = nj;
    }

    public Boolean call() {
        for (int i = this.NH * this.NG; i < Math.min(this.NI, (this.NG + 1) * this.NH); ++i) {
            try {
                this.NJ.TH(i);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return true;
    }
}