/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;

import static com.android.launcher3.WidgetPreviewLoader.CacheDb.DB_NAME;

public class ThemeChangedReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        LauncherAppState app = LauncherAppState.getInstance();
        clearWidgetPreviewCache(context);
        app.recreateWidgetPreviewDb();
        app.getIconCache().flush();
        app.getModel().forceReload();
    }

    /**
     * Normally we could use context.deleteDatabase() but this db is in cache/ so we'll
     * manually delete it and the journal ourselves.
     * @param context
     */
    private void clearWidgetPreviewCache(Context context) {
        File[] files = context.getCacheDir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isDirectory() && f.getName().startsWith(DB_NAME)) f.delete();
            }
        }
    }
}
