package com.android.launcher3.reflection.layers;

import java.util.ArrayList;

public class r extends v
{
    i[] Ob;
    int cellSize;

    void Ue() {
        super.Ue();
        final i[] ob = this.Ob;
        for (int length = ob.length, i = 0; i < length; ++i) {
            ob[i].TQ();
        }
    }

    public void Ui(final a a, final int n, final b b, final b b2, final b b3) {
        int i = 0;
        b.Tu(b, b2, this.Op, false);
        e.getInstance().TI(this.Ob.length, new w(this, n, (ArrayList[])this.Oo.SG(n), (b)this.On.SG(n), (b)this.Om.SG(n - 1)));
        this.Oq.clear();
        for (int j = 0; j < this.Ob.length; ++j) {
            this.Oq.TG(this.Ob[j].TR());
        }
        this.Or.clear();
        while (i < this.Ob.length) {
            this.Or.TG(this.Ob[i].TS());
            ++i;
        }
    }

    public b Uj(final boolean b, final a a, final ArrayList[] array, final b b2) {
        final int os = 1;
        b b3 = null;
        if (array == null) {
            this.Os = false;
            this.On.add(b2);
        }
        else {
            this.Os = (os != 0);
            this.Oo.add(array);
        }
        if (this.Om.SH() > os) {
            b3 = (b)this.Om.SI();
        }
        b b4 = (b)this.Om.SD();
        if (b4 == null) {
            b4 = new b(this.Ov, this.Ou);
        }
        final b b5 = (b)this.Om.add(b4);
        e.getInstance().TI(this.Ob.length, new g(this, array, b2, b3, b5));
        return b5;
    }

    public void Ut() {
        super.Ut();
        final i[] ob = this.Ob;
        for (int length = ob.length, i = 0; i < length; ++i) {
            ob[i].TT();
        }
    }

    public v clone() {
        return null;
    }

    public String getName() {
        return "LSTMLayer";
    }

    public void update() {
        final i[] ob = this.Ob;
        for (int length = ob.length, i = 0; i < length; ++i) {
            ob[i].update();
        }
    }
}
