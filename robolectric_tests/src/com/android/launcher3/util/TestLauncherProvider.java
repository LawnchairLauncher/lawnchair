package com.android.launcher3.util;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.android.launcher3.LauncherProvider;
import com.android.launcher3.provider.RestoreDbTask;

/**
 * An extension of LauncherProvider backed up by in-memory database.
 */
public class TestLauncherProvider extends LauncherProvider {

    private boolean mAllowLoadDefaultFavorites;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    protected synchronized void createDbIfNotExists() {
        if (mOpenHelper == null) {
            mOpenHelper = new MyDatabaseHelper(getContext(), mAllowLoadDefaultFavorites);
        }
    }

    public void setAllowLoadDefaultFavorites(boolean allowLoadDefaultFavorites) {
        mAllowLoadDefaultFavorites = allowLoadDefaultFavorites;
    }

    public SQLiteDatabase getDb() {
        createDbIfNotExists();
        return mOpenHelper.getWritableDatabase();
    }

    public SQLiteDatabase getDbWithRestoreDbTask() {
        RestoreDbTask.setPending(getContext(), true);
        super.createDbIfNotExists();
        return mOpenHelper.getWritableDatabase();
    }

    private static class MyDatabaseHelper extends DatabaseHelper {

        private final boolean mAllowLoadDefaultFavorites;

        MyDatabaseHelper(Context context, boolean allowLoadDefaultFavorites) {
            super(context, null);
            mAllowLoadDefaultFavorites = allowLoadDefaultFavorites;
            initIds();
        }

        @Override
        public long getDefaultUserSerial() {
            return 0;
        }

        @Override
        protected void onEmptyDbCreated() {
            if (mAllowLoadDefaultFavorites) {
                super.onEmptyDbCreated();
            }
        }

        @Override
        protected void handleOneTimeDataUpgrade(SQLiteDatabase db) { }
    }
}
