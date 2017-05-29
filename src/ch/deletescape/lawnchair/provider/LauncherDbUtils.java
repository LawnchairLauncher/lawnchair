/*
 * Copyright (C) 2016 The Android Open Source Project
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

package ch.deletescape.lawnchair.provider;

import android.database.Cursor;

import java.util.ArrayList;

import ch.deletescape.lawnchair.LauncherSettings.WorkspaceScreens;

/**
 * A set of utility methods for Launcher DB used for DB updates and migration.
 */
public class LauncherDbUtils {

    /**
     * Parses the cursor containing workspace screens table and returns the list of screen IDs
     */
    public static ArrayList<Long> getScreenIdsFromCursor(Cursor sc) {
        ArrayList<Long> screenIds = new ArrayList<>();
        try {
            final int idIndex = sc.getColumnIndexOrThrow(WorkspaceScreens._ID);
            while (sc.moveToNext()) {
                try {
                    screenIds.add(sc.getLong(idIndex));
                } catch (Exception ignored) {
                }
            }
        } finally {
            sc.close();
        }
        return screenIds;
    }
}
