package com.android.launcher3.reflection.b2;

import java.util.List;
import java.util.Map;
import java.io.Serializable;
import android.database.sqlite.SQLiteDatabase;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;
import java.util.ArrayList;
import android.content.ContentValues;
import com.android.launcher3.reflection.nano.a;
import com.android.launcher3.util.Preconditions;

public class d
{
    private final b V;

    public d(final b v) {
        this.V = v;
    }

    public void O(final long n) {
        synchronized (this) {
            Preconditions.assertNonUiThread();
            this.V.getWritableDatabase().delete("reflection_event", "timestamp <= ?", new String[] { Long.toString(n - 3024000000L) });
        }
    }

    public void P(final a a) {
        a from;
        SQLiteDatabase writableDatabase;
        ContentValues contentValues;
        Serializable s2 = null;
        Object o = null;
        try {
            Preconditions.assertNonUiThread();
            final byte[] byteArray = com.google.protobuf.nano.MessageNano.toByteArray(a);
            from = a.parseFrom(byteArray);
            writableDatabase = this.V.getWritableDatabase();
            contentValues = new ContentValues();
            final String s = "timestamp";
            s2 = from.LC;
            contentValues.put(s, (Long)s2);
            final String s3 = "client";
            s2 = from.LA;
            contentValues.put(s3, (String)s2);
            final String s4 = "type";
            s2 = from.Lz;
            contentValues.put(s4, (String)s2);
            final String s5 = "id";
            s2 = from.Ly;
            contentValues.put(s5, (String)s2);
            from.Ly = "";
            s2 = new ArrayList();
            final com.android.launcher3.reflection.nano.b[] li = from.LI;
            for (int i = 0; i < li.length; ++i) {
                o = li[i];
                if (!((com.android.launcher3.reflection.nano.b)o).LL.equals("lat_long")) {
                }
                final String s6 = "latLong";
                o = com.google.protobuf.nano.MessageNano.toByteArray((com.google.protobuf.nano.MessageNano)o);
                contentValues.put(s6, (byte[])o);
            }
        }
        finally {}
        Label_0270: {
            ((List<byte[]>)s2).add((byte[])o);
        }
        from.LI = ((List<byte[]>)s2).toArray(new com.android.launcher3.reflection.nano.b[((List)s2).size()]);
        contentValues.put("proto", com.google.protobuf.nano.MessageNano.toByteArray(from));
        writableDatabase.insert("reflection_event", null, contentValues);
    }

    public void Q(final String p0, final String p1, final Map p2) {
    }

    public e R(final long p0, final int p1) {
        return null;
    }
}