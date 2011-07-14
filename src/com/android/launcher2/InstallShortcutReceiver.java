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

package com.android.launcher2;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.android.launcher.R;

public class InstallShortcutReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_SHORTCUT =
            "com.android.launcher.action.INSTALL_SHORTCUT";

    // A mime-type representing shortcut data
    public static final String SHORTCUT_MIMETYPE =
            "com.android.launcher/shortcut";

    private final int[] mCoordinates = new int[2];

    public void onReceive(Context context, Intent data) {
        if (!ACTION_INSTALL_SHORTCUT.equals(data.getAction())) {
            return;
        }

        int screen = Launcher.getScreen();

        if (!installShortcut(context, data, screen)) {
            // The target screen is full, let's try the other screens
            for (int i = 0; i < Launcher.SCREEN_COUNT; i++) {
                if (i != screen && installShortcut(context, data, i)) break;
            }
        }
    }

    private boolean installShortcut(Context context, Intent data, int screen) {
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (findEmptyCell(context, mCoordinates, screen)) {
            Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            if (intent != null) {
                if (intent.getAction() == null) {
                    intent.setAction(Intent.ACTION_VIEW);
                }

                // By default, we allow for duplicate entries (located in
                // different places)
                boolean duplicate = data.getBooleanExtra(Launcher.EXTRA_SHORTCUT_DUPLICATE, true);
                if (duplicate || !LauncherModel.shortcutExists(context, name, intent)) {
                    LauncherApplication app = (LauncherApplication) context.getApplicationContext();
                    app.getModel().addShortcut(context, data,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, screen, mCoordinates[0],
                            mCoordinates[1], true);
                    Toast.makeText(context, context.getString(R.string.shortcut_installed, name),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, context.getString(R.string.shortcut_duplicate, name),
                            Toast.LENGTH_SHORT).show();
                }

                return true;
            }
        } else {
            Toast.makeText(context, context.getString(R.string.out_of_space),
                    Toast.LENGTH_SHORT).show();
        }

        return false;
    }

    private static boolean findEmptyCell(Context context, int[] xy, int screen) {
        final int xCount = LauncherModel.getCellCountX();
        final int yCount = LauncherModel.getCellCountY();
        boolean[][] occupied = new boolean[xCount][yCount];

        ArrayList<ItemInfo> items = LauncherModel.getItemsInLocalCoordinates(context);
        ItemInfo item = null;
        int cellX, cellY, spanX, spanY;
        for (int i = 0; i < items.size(); ++i) {
            item = items.get(i);
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                if (item.screen == screen) {
                    cellX = item.cellX;
                    cellY = item.cellY;
                    spanX = item.spanX;
                    spanY = item.spanY;
                    for (int x = cellX; x < cellX + spanX && x < xCount; x++) {
                        for (int y = cellY; y < cellY + spanY && y < yCount; y++) {
                            occupied[x][y] = true;
                        }
                    }
                }
            }
        }

        return CellLayout.findVacantCell(xy, 1, 1, xCount, yCount, occupied);
    }
}
