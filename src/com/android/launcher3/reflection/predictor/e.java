package com.android.launcher3.reflection.predictor;

import com.google.protobuf.nano.ExtendableMessageNano;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import com.android.launcher3.reflection.common.nano.a;
import com.android.launcher3.reflection.common.b;

public class e
{
    private long Lq;
    private int Lr;
    private b Ls;
    private boolean Lt;

    public e() {
        this.Lq = 3600000L;
        this.Lr = 100;
        this.Ls = new b(this.Lr, false);
    }

    private boolean Sl(final a a) {
        boolean b = false;
        if (a.Lz == null || a.Lz.equals("app_launch")) {
            b = true;
        }
        return b;
    }

    public static e Sp(final byte[] array) {
        final e e = new e();
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(array);
        final DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
        final e e2 = e;
        try {
            e2.So(dataInputStream);
            dataInputStream.close();
            return e;
        }
        catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static byte[] Sq(final e e) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        try {
            e.Sn(dataOutputStream);
            dataOutputStream.close();
            return byteArrayOutputStream.toByteArray();
        }
        catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static e Sr(final String s) {
        e sp = new e();
        if (s != null) {
            sp = Sp(s.getBytes(StandardCharsets.ISO_8859_1));
        }
        return sp;
    }

    public static String Ss(final e e) {
        if (e == null) {
            return null;
        }
        return new String(Sq(e), StandardCharsets.ISO_8859_1);
    }

    public void Sk(final a a) throws Exception {
        final boolean lt = true;
        if (this.Sl(a)) {
            Label_0028:
            while (this.Ls.SF() > 0) {
                Label_0119: {
                    try {
                        final Object sg = this.Ls.SG(0);
                        long sr = Long.MAX_VALUE;
                        try {
                            sr = com.android.launcher3.reflection.common.e.SR((a)sg, a);
                        }
                        catch (Exception ex) {
                        }
                        if (sr > this.Lq) {
                            break Label_0119;
                        }
                        final int n = lt ? 1 : 0;
                        if (n == 0) {
                            this.Ls.SJ();
                            break;
                        }
                        break Label_0028;
                    }
                    catch (Exception ex2) {}
                }
                continue;
            }
            this.Ls.add(a);
            this.Lt = lt;
        }
    }

    public b Sm() {
        return this.Ls;
    }

    public void Sn(final DataOutputStream dataOutputStream) throws IOException {
        final int size = this.size();
        dataOutputStream.writeInt(size);
        for (int i = 0; i < size; ++i) {
            final byte[] byteArray = com.google.protobuf.nano.MessageNano.toByteArray((com.google.protobuf.nano.MessageNano)this.Ls.SG(i));
            dataOutputStream.writeInt(byteArray.length);
            dataOutputStream.write(byteArray, 0, byteArray.length);
        }
        this.Lt = false;
    }

    public void So(final DataInputStream dataInputStream) throws IOException {
        byte[] array = null;
        this.clear();
        for (int int1 = dataInputStream.readInt(), i = 0; i < int1; ++i) {
            final int int2 = dataInputStream.readInt();
            if (array == null || array.length < int2) {
                array = new byte[int2];
            }
            dataInputStream.read(array, 0, int2);
            final a sx = a.Sx(CodedInputByteBufferNano.newInstance(array, 0, int2));
            if (this.Sl(sx)) {
                try {
                    this.Sk(sx);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public void clear() {
        this.Ls.clear();
    }

    public int size() {
        return this.Ls.SF();
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("(size ");
        sb.append(this.Ls.SF());
        sb.append("): ");
        for (int i = 0; i < this.Ls.SF(); ++i) {
            sb.append(((a)this.Ls.SG(i)).Ly);
            sb.append(" ");
        }
        sb.append("\n");
        return sb.toString();
    }
}