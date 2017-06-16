package com.android.launcher3.reflection.layers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class m extends o
{
    public int NF;

    public m() {
        this.NF = 0;
    }

    public m(final int nf, final int n, final int n2, final int n3, final int n4, final int n5, final int n6, final int n7, final boolean b) {
        super(false, n, n2, n3, n4, n5, n6, n7, b, false, 0.0f);
        this.NF = 0;
        this.NF = nf;
    }

    void Ua(final int n, final b b, final b b2, final b b3) {
        e.getInstance().TI(b.MC.length, new d(this, n, b, b2, b3));
    }

    public void Ub(final DataOutputStream dataOutputStream) throws IOException {
        super.Ub(dataOutputStream);
        dataOutputStream.writeInt(this.NF);
        this.UA(dataOutputStream);
    }

    public void Uc(final DataInputStream dataInputStream) throws IOException {
        super.Uc(dataInputStream);
        this.NF = dataInputStream.readInt();
        this.UB(dataInputStream);
    }

    public m clone() {
        final m m = new m();
        super.Uh(m);
        m.NF = this.NF;
        return m;
    }

    public String getName() {
        return "OutputLayer";
    }
}