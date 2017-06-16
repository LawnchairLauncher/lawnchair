package com.android.launcher3.reflection.layers;

import java.io.IOException;
import java.util.ArrayList;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import com.android.launcher3.reflection.common.b;

public abstract class v
{
    b Om;
    b On;
    b Oo;
    com.android.launcher3.reflection.layers.b Op;
    com.android.launcher3.reflection.layers.b Oq;
    com.android.launcher3.reflection.layers.b Or;
    boolean Os;
    int Ot;
    int Ou;
    int Ov;
    boolean Ow;

    v() {
    }

    v(final boolean ow, final int n, final int ov, final int ot, final int ou) {
        final boolean b = true;
        this.Om = new b(n, b);
        this.On = new b(n, b);
        this.Oo = new b(n, b);
        this.Ov = ov;
        this.Ot = ot;
        this.Ou = ou;
        this.Ow = ow;
        this.Op = new com.android.launcher3.reflection.layers.b(ov, ou);
        this.Oq = new com.android.launcher3.reflection.layers.b(ov, ot);
        this.Or = new com.android.launcher3.reflection.layers.b(ov, ou);
        l.TU(this.Oq.MC);
        l.TU(this.Or.MC);
        l.TU(this.Op.MC);
    }

    void UA(final DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeUTF(this.getName());
    }

    void UB(final DataInputStream dataInputStream) throws IOException {
        final String utf = dataInputStream.readUTF();
        if (utf.equals(this.getName())) {
            return;
        }
        final String value = String.valueOf(this.getName());
        throw new RuntimeException(new StringBuilder(String.valueOf(value).length() + 19 + String.valueOf(utf).length()).append("Expected ").append(value).append(" acquired ").append(utf).toString());
    }

    public void Ub(final DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeInt(this.Ov);
        dataOutputStream.writeInt(this.Ou);
        dataOutputStream.writeInt(this.Ot);
        dataOutputStream.writeInt(this.Om.SH());
        dataOutputStream.writeBoolean(this.Ow);
    }

    public void Uc(final DataInputStream dataInputStream) throws IOException {
        final boolean b = true;
        this.Ov = dataInputStream.readInt();
        this.Ou = dataInputStream.readInt();
        this.Ot = dataInputStream.readInt();
        final int int1 = dataInputStream.readInt();
        this.Ow = dataInputStream.readBoolean();
        this.Om = new b(int1, b);
        this.On = new b(int1, b);
        this.Oo = new b(int1, b);
        this.Op = new com.android.launcher3.reflection.layers.b(this.Ov, this.Ou);
        this.Oq = new com.android.launcher3.reflection.layers.b(this.Ov, this.Ot);
        this.Or = new com.android.launcher3.reflection.layers.b(this.Ov, this.Ou);
        l.TU(this.Oq.MC);
        l.TU(this.Or.MC);
        l.TU(this.Op.MC);
    }

    void Ue() {
        l.TU(this.Oq.MC);
        l.TU(this.Or.MC);
        l.TU(this.Op.MC);
    }

    public abstract void Ui(final a p0, final int p1, final com.android.launcher3.reflection.layers.b p2, final com.android.launcher3.reflection.layers.b p3, final com.android.launcher3.reflection.layers.b p4);

    public abstract com.android.launcher3.reflection.layers.b Uj(final boolean p0, final a p1, final ArrayList[] p2, final com.android.launcher3.reflection.layers.b p3);

    public void Ut() {
        this.Om.clear();
        this.On.clear();
        this.Oo.clear();
    }

    public void Uu(final v v) {
        final boolean b = true;
        final int sh = this.Om.SH();
        v.Om = new b(sh, b);
        v.On = new b(sh, b);
        v.Oo = new b(sh, b);
        v.Ot = this.Ot;
        v.Ou = this.Ou;
        v.Ov = this.Ov;
        v.Ow = this.Ow;
        v.Op = new com.android.launcher3.reflection.layers.b(this.Ov, this.Ou);
        v.Oq = new com.android.launcher3.reflection.layers.b(this.Ov, this.Ot);
        v.Or = new com.android.launcher3.reflection.layers.b(this.Ov, this.Ou);
        l.TU(v.Oq.MC);
        l.TU(v.Or.MC);
        l.TU(v.Op.MC);
    }

    public int Uv() {
        return this.Ot;
    }

    public int Uw() {
        return this.Ou;
    }

    public int Ux() {
        return this.Om.SH();
    }

    public com.android.launcher3.reflection.layers.b Uy() {
        return this.Oq;
    }

    public com.android.launcher3.reflection.layers.b Uz() {
        return this.Or;
    }

    public abstract v clone();

    public abstract String getName();

    public abstract void update();
}
