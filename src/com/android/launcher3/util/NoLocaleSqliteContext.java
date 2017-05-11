package com.android.launcher3.util;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

/**
 * A context wrapper which creates databases without support for localized collators.
 */
public class NoLocaleSqliteContext extends ContextWrapper {

    public NoLocaleSqliteContext(Context context) {
        super(context);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(
            String name, int mode, CursorFactory factory, DatabaseErrorHandler errorHandler) {
        return super.openOrCreateDatabase(
                name, mode | Context.MODE_NO_LOCALIZED_COLLATORS, factory, errorHandler);
    }
}
