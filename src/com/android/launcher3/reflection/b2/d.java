package com.android.launcher3.reflection.b2;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.io.Serializable;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import android.content.ContentValues;
import android.database.sqlite.SQLiteStatement;

import com.android.launcher3.reflection.common.nano.a;
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
            final com.android.launcher3.reflection.common.nano.b[] li = from.LI;
            for (int i = 0; i < li.length; ++i) {
                o = li[i];
                if (!((com.android.launcher3.reflection.common.nano.b)o).LL.equals("lat_long")) {
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
        from.LI = ((List<byte[]>)s2).toArray(new com.android.launcher3.reflection.common.nano.b[((List)s2).size()]);
        contentValues.put("proto", com.google.protobuf.nano.MessageNano.toByteArray(from));
        writableDatabase.insert("reflection_event", null, contentValues);
    }

    public void Q(final String p0, final String p1, final Map p2) {
        synchronized (this) {
            Preconditions.assertNonUiThread();
            SQLiteDatabase writableDatabase = this.V.getWritableDatabase();
            writableDatabase.beginTransaction();

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(p0);
            stringBuilder.append("%");

            Cursor cursor = writableDatabase.query("reflection_event", new String[]{"id", "_id"}, "id like ?", new String[]{stringBuilder.toString()}, null, null, null, null);

            SQLiteStatement sqLiteStatement = writableDatabase.compileStatement("UPDATE reflection_event SET id = ? WHERE _id = ?");

            int columnIndex10 = cursor.getColumnIndex("_id");
            int columnIndex14 = cursor.getColumnIndexOrThrow("id");
            while (cursor.moveToNext()) {
                Long l = cursor.getLong(columnIndex10);
                String s16 = cursor.getString(columnIndex14);
                String s6 = (String) p2.get(s16);
                if (s6 == null) {
                    s6 = new StringBuilder().append(p1).append("_").append(p2.size()).toString();
                    p2.put(s16, s6);
                }

                sqLiteStatement.bindString(1, s6);
                sqLiteStatement.bindLong(2, l);
                sqLiteStatement.executeUpdateDelete();
            }

            sqLiteStatement.close();
            cursor.close();

            writableDatabase.endTransaction();
        }
    }

    public e R(final long p0, final int p1) {

        SQLiteDatabase a4 = V.getReadableDatabase();
        ArrayList a5 = new ArrayList();

        Cursor a12 = a4.query("reflection_event", null, String.format(Locale.US, "%s > ?", new String[] { "_id" }), new String[] { Long.toString(p0) }, null, null, "_id ASC", Integer.toString(p1));

        int i22 = a12.getColumnIndex("_id");
        int i14 = a12.getColumnIndex("proto");
        int i19 = a12.getColumnIndex("id");
        int i17 = a12.getColumnIndex("latLong");
        int i23 = a12.getColumnIndex("semanticPlace");

        long l24 = 0;
        while (a12.moveToNext()) {
            com.android.launcher3.reflection.common.nano.a a21 = com.android.launcher3.reflection.common.nano.a.parseFrom(a12.getBlob(i14));
            a21.Ly = a12.getString(i19);
            if (!a12.isNull(i17)) {
                com.android.launcher3.reflection.common.nano.b a10 = com.android.launcher3.reflection.common.nano.b.parseFrom(a12.getBlob(i17));
                com.android.launcher3.reflection.common.a.Sy(a21, a10);
            }
            if (!a12.isNull(i23)) {
                com.android.launcher3.reflection.common.nano.b a10 = com.android.launcher3.reflection.common.nano.b.parseFrom(a12.getBlob(i23));
                com.android.launcher3.reflection.common.a.Sy(a21, a10);
            }
            a5.add(a21);
            l24 = a12.getLong(i22);
        }

        a12.close();
        return new e(l24, a5);

    }
}