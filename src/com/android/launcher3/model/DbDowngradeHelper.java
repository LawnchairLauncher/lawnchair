/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.model;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.util.SparseArray;

import com.android.launcher3.R;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.util.IOUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Utility class to handle DB downgrade
 */
public class DbDowngradeHelper {

    private static final String TAG = "DbDowngradeHelper";

    private static final String KEY_VERSION = "version";
    private static final String KEY_DOWNGRADE_TO = "downgrade_to_";

    private final SparseArray<String[]> mStatements = new SparseArray<>();
    public final int version;

    private DbDowngradeHelper(int version) {
        this.version = version;
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ArrayList<String> allCommands = new ArrayList<>();

        for (int i = oldVersion - 1; i >= newVersion; i--) {
            String[] commands = mStatements.get(i);
            if (commands == null) {
                throw new SQLiteException("Downgrade path not supported to version " + i);
            }
            Collections.addAll(allCommands, commands);
        }

        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            for (String sql : allCommands) {
                db.execSQL(sql);
            }
            t.commit();
        }
    }

    public static DbDowngradeHelper parse(File file) throws JSONException, IOException {
        JSONObject obj = new JSONObject(new String(IOUtils.toByteArray(file)));
        DbDowngradeHelper helper = new DbDowngradeHelper(obj.getInt(KEY_VERSION));
        for (int version = helper.version - 1; version > 0; version--) {
            if (obj.has(KEY_DOWNGRADE_TO + version)) {
                JSONArray statements = obj.getJSONArray(KEY_DOWNGRADE_TO + version);
                String[] parsed = new String[statements.length()];
                for (int i = 0; i < parsed.length; i++) {
                    parsed[i] = statements.getString(i);
                }
                helper.mStatements.put(version, parsed);
            }
        }
        return helper;
    }

    public static void updateSchemaFile(File schemaFile, int expectedVersion, Context context) {
        try {
            if (DbDowngradeHelper.parse(schemaFile).version >= expectedVersion) {
                return;
            }
        } catch (Exception e) {
            // Schema error
        }

        // Write the updated schema
        try (FileOutputStream fos = new FileOutputStream(schemaFile);
            InputStream in = context.getResources().openRawResource(R.raw.downgrade_schema)) {
            IOUtils.copy(in, fos);
        } catch (IOException e) {
            Log.e(TAG, "Error writing schema file", e);
        }
    }
}
