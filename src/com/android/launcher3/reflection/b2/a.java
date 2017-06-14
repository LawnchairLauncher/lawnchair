package com.android.launcher3.reflection.b2;

import java.io.Closeable;
import com.android.launcher3.Utilities;
import android.util.Log;
import java.io.IOException;
import java.io.EOFException;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import com.android.launcher3.util.Preconditions;
import java.util.Arrays;
import java.util.List;
import java.io.File;

public class a
{
    private final long Q;
    private final File R;

    public a(final File r, final long q) {
        this.R = r;
        this.Q = q;
    }

    private void K() {
        if (this.R.exists() && this.R.length() > this.Q) {
            final List l = this.L();
            if (this.R.delete()) {
                this.log(l.subList(l.size() / 2, l.size()));
            }
        }
    }

    public void J(final com.android.launcher3.reflection.c2.a a) {
        final int n = 1;
        try {
            final com.android.launcher3.reflection.c2.a[] array = new com.android.launcher3.reflection.c2.a[n];
            array[0] = a;
            this.log(Arrays.asList(array));
            this.K();
        }
        finally {
        }
    }

    public List L() {
        while (true) {
            ArrayList<com.android.launcher3.reflection.c2.a> list = new ArrayList<>();
            CodedInputByteBufferNano qq;
            try {
                Preconditions.assertNonUiThread();
                final FileInputStream fileInputStream = new FileInputStream(this.R);
                final BufferedInputStream bufferedInputStream = new java.io.BufferedInputStream(fileInputStream);
                final DataInputStream dataInputStream = new DataInputStream(bufferedInputStream);

                Object o = null;
                while (true) {
                    final int int1 = dataInputStream.readInt();
                    if (o == null || ((byte[])o).length < int1) {
                        o = new byte[int1];
                    }
                    dataInputStream.read((byte[])o, 0, int1);
                    qq = CodedInputByteBufferNano.newInstance((byte[])o, 0, int1);

                    final CodedInputByteBufferNano c = qq;
                    final com.android.launcher3.reflection.c2.a a = com.android.launcher3.reflection.c2.a.S(c);
                    final com.android.launcher3.reflection.c2.a a3;
                    final com.android.launcher3.reflection.c2.a a2 = a3 = a;
                    if (a3 != null) {
                        final ArrayList<com.android.launcher3.reflection.c2.a> list2 = list;
                        final com.android.launcher3.reflection.c2.a a4 = a2;
                        list2.add(a4);
                        continue;
                    }
                    break;
                }

                final CodedInputByteBufferNano c = qq;
                final com.android.launcher3.reflection.c2.a a = com.android.launcher3.reflection.c2.a.S(c);
                final com.android.launcher3.reflection.c2.a a3;
                final com.android.launcher3.reflection.c2.a a2 = a3 = a;
                if (a3 != null) {
                    final ArrayList<com.android.launcher3.reflection.c2.a> list2 = list;
                    final com.android.launcher3.reflection.c2.a a4 = a2;
                    list2.add(a4);
                    continue;
                }

                Log.e("Reflection.ClientActLog", "Failed in loading the log file", (Throwable)o);
                Utilities.closeSilently(dataInputStream);
                return list;
            }
            catch (EOFException ex16) {}
            catch (IOException ex15) {}
            finally {}

            break;
        }

        return null;
    }

    void log(final List p0) {
    }
}
