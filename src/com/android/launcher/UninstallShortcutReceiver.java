/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ContentResolver;
import android.database.Cursor;

import java.net.URISyntaxException;

import com.android.internal.provider.Settings;

public class UninstallShortcutReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent data) {
        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        boolean duplicate = data.getBooleanExtra(Launcher.EXTRA_SHORTCUT_DUPLICATE, true);

        if (intent != null && name != null) {
            final ContentResolver cr = context.getContentResolver();
            Cursor c = cr.query(Settings.Favorites.CONTENT_URI,
                new String[] { "_id", "intent" }, "title=?", new String[]{ name }, null);

            final int intentIndex = c.getColumnIndexOrThrow(Settings.Favorites.INTENT);
            final int idIndex = c.getColumnIndexOrThrow(Settings.Favorites._ID);

            try {
                while (c.moveToNext()) {
                    try {
                        if (intent.filterEquals(Intent.getIntent(c.getString(intentIndex)))) {
                            final long id = c.getLong(idIndex);
                            cr.delete(Settings.Favorites.getContentUri(id, false), null, null);
                            if (!duplicate) {
                                break;
                            }
                        }
                    } catch (URISyntaxException e) {
                        // Ignore
                    }
                }
            } finally {
                c.close();
            }

            cr.notifyChange(Settings.Favorites.CONTENT_URI, null);
        }
    }
}
