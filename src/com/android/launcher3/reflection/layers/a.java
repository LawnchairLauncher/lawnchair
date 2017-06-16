package com.android.launcher3.reflection.layers;

import java.io.IOException;
import java.io.DataInputStream;
import java.util.Iterator;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import com.android.launcher3.reflection.common.b;

public class a
{
    b My;
    List Mz;

    public a(final int n) {
        this.Mz = new ArrayList();
        this.My = new b(n, true);
    }

    private void To() {
        for (int i = 0; i < this.Mz.size(); ++i) {
            ((v)this.Mz.get(i)).Ue();
        }
    }

    public List Tk() {
        return this.Mz;
    }

    public void Tl(final v v) {
        if (v.Ux() != this.My.SH()) {
            throw new RuntimeException(new StringBuilder(102).append("Inconsistent framebuffer size with the added layer: targetsize=").append(this.My.SH()).append(" layerbuffersize=").append(v.Ux()).toString());
        }
        if (v instanceof o && ((o)v).Ul() == this.Mz.size()) {
            throw new RuntimeException();
        }
        this.Mz.add(v);
    }

    public com.android.launcher3.reflection.layers.b Tm(final boolean b, ArrayList[] array, com.android.launcher3.reflection.layers.b b2, final boolean b3) {
        if (b3 && !(this.Mz.get(this.Mz.size() - 1) instanceof m)) {
            throw new RuntimeException("Lacks outputlayer");
        }
        int i = 0;
        com.android.launcher3.reflection.layers.b b4 = null;
        while (i < this.Mz.size()) {
            final com.android.launcher3.reflection.layers.b uj = ((v)this.Mz.get(i)).Uj(b, this, array, b2);
            ++i;
            b2 = uj;
            array = null;
            b4 = uj;
        }
        return b4;
    }

    public void Tn(final com.android.launcher3.reflection.layers.b b, final com.android.launcher3.reflection.layers.b b2, final int n, final boolean b3) {
        if (b3 && !(this.Mz.get(this.Mz.size() - 1) instanceof m)) {
            throw new RuntimeException("Lacks outputlayer");
        }
        this.My.add(b);
        final int sc = this.My.SC();
        this.To();
        for (int sf = this.My.SF(), n2 = sf - 1; n2 >= 0 && sf - 1 - n2 < n; --n2) {
            final int size = this.Mz.size();
            final com.android.launcher3.reflection.layers.b b4 = (com.android.launcher3.reflection.layers.b)this.My.SG(n2);
            int i = size - 1;
            com.android.launcher3.reflection.layers.b uy = b4;
            while (i >= 0) {
                final v v = (v) this.Mz.get(i);
                if (!v.Os) {
                    if (v.On.SC() != sc) {
                        throw new RuntimeException(new StringBuilder(110).append("backward: dense input vector has a different frame index from the target frame index: ").append(v.On.SC()).append("!=").append(sc).toString());
                    }
                }
                else if (v.Oo.SC() != sc) {
                    throw new RuntimeException("backward: sparse input vector has a different frame index from the target frame index");
                }
                v.Ui(this, n2, uy, v.Uz(), b2);
                uy = v.Uy();
                --i;
            }
        }
    }

    public int Tp() {
        return ((v)this.Mz.get(0)).Uv();
    }

    public void Tq(final DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeInt(this.Mz.size());
        for (final Object o : this.Mz) {
            v v = (v) o;
            dataOutputStream.writeUTF(v.getName());
            v.Ub(dataOutputStream);
        }
        dataOutputStream.writeUTF("NeuralNet");
    }

    public void Tr(final DataInputStream dataInputStream) throws IOException {
        this.Mz.clear();
        for (int int1 = dataInputStream.readInt(), i = 0; i < int1; ++i) {
            final String utf = dataInputStream.readUTF();
            v v;
            if (!utf.equals("LinearLayer")) {
                if (!utf.equals("OutputLayer")) {
                    if (!utf.equals("LSTMLayer")) {
                        final String s = "Unsupported layer type: ";
                        final String value = String.valueOf(utf);
                        String concat;
                        if (value.length() == 0) {
                            concat = new String(s);
                        }
                        else {
                            concat = s.concat(value);
                        }
                        throw new IOException(concat);
                    }
                    v = new r();
                }
                else {
                    v = new m();
                }
            }
            else {
                v = new o();
            }
            v.Uc(dataInputStream);
            this.Mz.add(v);
        }
        final String utf2 = dataInputStream.readUTF();
        if (utf2.equals("NeuralNet")) {
            return;
        }
        throw new IOException(new StringBuilder(String.valueOf(utf2).length() + 45).append("Inconsistent ending: [").append(utf2).append("] expected: [NeuralNet]").toString());
    }

    public v Ts() {
        return (v) this.Mz.get(this.Mz.size() - 1);
    }

    public void Tt() {
        this.My.clear();
        final Iterator<v> iterator = this.Mz.iterator();
        while (iterator.hasNext()) {
            iterator.next().Ut();
        }
    }

    public a clone() {
        final a a = new a(this.My.SH());
        final Iterator<v> iterator = this.Mz.iterator();
        while (iterator.hasNext()) {
            a.Mz.add(iterator.next().clone());
        }
        return a;
    }

    public void update() {
        for (int i = this.Mz.size() - 1; i >= 0; --i) {
            ((v)this.Mz.get(i)).update();
        }
    }
}
