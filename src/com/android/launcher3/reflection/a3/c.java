package com.android.launcher3.reflection.a3;

import java.util.regex.Pattern;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

public class c extends b implements a
{
    private List Mq;
    private int Mr;

    public c() {
        this.Mq = new ArrayList();
        this.Mr = 0;
    }

    private int Tf(final b b) {
        final Iterator<b> iterator = this.Mq.iterator();
        int n = 0;
        while (iterator.hasNext()) {
            final b b2 = iterator.next();
            if (b2 == b) {
                break;
            }
            n += b2.SW();
        }
        return n;
    }

    public com.android.launcher3.reflection.layers.b SV(final com.android.launcher3.reflection.common.b b, final com.android.launcher3.reflection.common.nano.a a) {
        final com.android.launcher3.reflection.layers.b b2 = new com.android.launcher3.reflection.layers.b(1, this.Mr);
        final Iterator<b> iterator = this.Mq.iterator();
        int n = 0;
        while (iterator.hasNext()) {
            final b b3 = iterator.next();
            final double[] mc = b3.SV(b, a).MC;
            for (int i = 0; i < mc.length; ++i) {
                b2.MC[i + n] = mc[i];
            }
            n += b3.SW();
        }
        return b2;
    }

    public int SW() {
        return this.Mr;
    }

    public void SX(final DataInputStream dataInputStream) throws IOException {
        int i = 0;
        this.Mq.clear();
        this.Mr = 0;
        while (i < dataInputStream.readInt()) {
            final String utf = dataInputStream.readUTF();
            final b tb = b.Tb(utf);
            if (tb == null) {
                final String s = "Cannot find extractor with ";
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
            tb.SX(dataInputStream);
            this.Te(tb);
            ++i;
        }
    }

    public void SY(final DataOutputStream dataOutputStream) throws IOException {
        dataOutputStream.writeInt(this.Mq.size());
        for (final Object o : this.Mq) {
            b b = (b) o;
            dataOutputStream.writeUTF(b.Ta(b));
            b.SY(dataOutputStream);
        }
    }

    public void SZ(final List list) {
        if (list.size() >= 2) {
            final Pattern compile = Pattern.compile((String) list.get(0));
            for (final Object o : this.Mq) {
                b b = (b) o;
                if (compile.matcher(b.getClass().getName()).matches()) {
                    b.SZ(list.subList(1, list.size()));
                }
            }
        }
    }

    public void Sv(final b b, final int n) {
        final int n2 = this.Tf(b) + n;
        if (this.Mp != null) {
            this.Mp.Sv(this, n2);
        }
    }

    public void Te(final b b) {
        this.Mq.add(b);
        b.Tc(this);
        this.Mr += b.SW();
    }

    public c clone() {
        final c c = new c();
        final Iterator<b> iterator = this.Mq.iterator();
        while (iterator.hasNext()) {
            c.Te(iterator.next().clone());
        }
        return c;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.Mq.size(); ++i) {
            sb.append(((b)this.Mq.get(i)).toString());
        }
        return sb.toString();
    }
}