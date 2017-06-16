package com.android.launcher3.reflection.layers;

class f implements c
{
    final /* synthetic */ int MO;
    final /* synthetic */ b MP;
    final /* synthetic */ b MQ;

    f(final int mo, final b mp, final b mq) {
        this.MO = mo;
        this.MP = mp;
        this.MQ = mq;
    }

    public Boolean TH(final int n) {
        final boolean b = true;
        if (this.MO != (b ? 1 : 0)) {
            this.MP.MC[n] = l.TW(this.MQ.MC[n]);
        }
        else {
            this.MP.MC[n] = l.TX(this.MQ.MC[n]);
        }
        return b;
    }
}
