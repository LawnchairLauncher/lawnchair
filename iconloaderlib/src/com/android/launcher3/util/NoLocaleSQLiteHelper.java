/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.util;

import static android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDatabase.OpenParams;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

/**
 * Extension of {@link SQLiteOpenHelper} which avoids creating default locale table by
 * A context wrapper which creates databases without support for localized collators.
 */
public abstract class NoLocaleSQLiteHelper extends SQLiteOpenHelper {

    private static final boolean ATLEAST_P =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;

    public NoLocaleSQLiteHelper(Context context, String name, int version) {
        super(ATLEAST_P ? context : new NoLocalContext(context), name, null, version);
        if (ATLEAST_P) {
            setOpenParams(new OpenParams.Builder().addOpenFlags(NO_LOCALIZED_COLLATORS).build());
        }
    }

    private static class NoLocalContext extends ContextWrapper {
        public NoLocalContext(Context base) {
            super(base);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, CursorFactory factory, DatabaseErrorHandler errorHandler) {
            return super.openOrCreateDatabase(
                    name, mode | Context.MODE_NO_LOCALIZED_COLLATORS, factory, errorHandler);
        }
    }
}
