package com.android.launcher3.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * An extension of {@link SQLiteOpenHelper} with utility methods for a single table cache DB.
 * Any exception during write operations are ignored, and any version change causes a DB reset.
 */
public abstract class SQLiteCacheHelper {
    private static final String TAG = "SQLiteCacheHelper";

    private static final boolean IN_MEMORY_CACHE = false;

    private final String mTableName;
    private final MySQLiteOpenHelper mOpenHelper;

    private boolean mIgnoreWrites;

    public SQLiteCacheHelper(Context context, String name, int version, String tableName) {
        if (IN_MEMORY_CACHE) {
            name = null;
        }
        mTableName = tableName;
        mOpenHelper = new MySQLiteOpenHelper(context, name, version);

        mIgnoreWrites = false;
    }

    /**
     * @see SQLiteDatabase#delete(String, String, String[])
     */
    public void delete(String whereClause, String[] whereArgs) {
        if (mIgnoreWrites) {
            return;
        }
        try {
            mOpenHelper.getWritableDatabase().delete(mTableName, whereClause, whereArgs);
        } catch (SQLiteFullException e) {
            onDiskFull(e);
        } catch (SQLiteException e) {
            Log.d(TAG, "Ignoring sqlite exception", e);
        }
    }

    /**
     * @see SQLiteDatabase#insertWithOnConflict(String, String, ContentValues, int)
     */
    public void insertOrReplace(ContentValues values) {
        if (mIgnoreWrites) {
            return;
        }
        try {
            mOpenHelper.getWritableDatabase().insertWithOnConflict(
                    mTableName, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLiteFullException e) {
            onDiskFull(e);
        } catch (SQLiteException e) {
            Log.d(TAG, "Ignoring sqlite exception", e);
        }
    }

    private void onDiskFull(SQLiteFullException e) {
        Log.e(TAG, "Disk full, all write operations will be ignored", e);
        mIgnoreWrites = true;
    }

    /**
     * @see SQLiteDatabase#query(String, String[], String, String[], String, String, String)
     */
    public Cursor query(String[] columns, String selection, String[] selectionArgs) {
        return mOpenHelper.getReadableDatabase().query(
                mTableName, columns, selection, selectionArgs, null, null, null);
    }

    public void clear() {
        mOpenHelper.clearDB(mOpenHelper.getWritableDatabase());
    }

    public void close() {
        mOpenHelper.close();
    }

    protected abstract void onCreateTable(SQLiteDatabase db);

    /**
     * A private inner class to prevent direct DB access.
     */
    private class MySQLiteOpenHelper extends NoLocaleSQLiteHelper {

        public MySQLiteOpenHelper(Context context, String name, int version) {
            super(context, name, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            onCreateTable(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                clearDB(db);
            }
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                clearDB(db);
            }
        }

        private void clearDB(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + mTableName);
            onCreate(db);
        }
    }
}
