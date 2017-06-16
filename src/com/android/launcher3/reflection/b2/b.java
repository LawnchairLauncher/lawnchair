package com.android.launcher3.reflection.b2;

import android.database.sqlite.SQLiteDatabase;
import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;

public class b extends SQLiteOpenHelper
{
    public b(final Context context, final String s) {
        super(context, s, null, 1);
    }

    public void onCreate(final SQLiteDatabase sqLiteDatabase) {
        final String s = "CREATE TABLE reflection_event (_id INTEGER PRIMARY KEY AUTOINCREMENT,timestamp INTEGER,client TEXT,type TEXT,id TEXT,latLong BLOB,semanticPlace BLOB,proto BLOB)";
        synchronized (this) {
            try {
                sqLiteDatabase.execSQL(s);
            }
            finally {
            }
        }
    }

    public void onUpgrade(final SQLiteDatabase sqLiteDatabase, final int n, final int n2) {
    }
}
