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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.android.launcher.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.json.*;

public class InstallShortcutReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_SHORTCUT =
            "com.android.launcher.action.INSTALL_SHORTCUT";
    public static final String NEW_APPS_PAGE_KEY = "apps.new.page";
    public static final String NEW_APPS_LIST_KEY = "apps.new.list";

    public static final String DATA_INTENT_KEY = "intent.data";
    public static final String LAUNCH_INTENT_KEY = "intent.launch";
    public static final String NAME_KEY = "name";
    public static final String ICON_KEY = "icon";
    public static final String ICON_RESOURCE_NAME_KEY = "iconResource";
    public static final String ICON_RESOURCE_PACKAGE_NAME_KEY = "iconResourcePackage";
    // The set of shortcuts that are pending install
    public static final String APPS_PENDING_INSTALL = "apps_to_install";

    public static final int NEW_SHORTCUT_BOUNCE_DURATION = 450;
    public static final int NEW_SHORTCUT_STAGGER_DELAY = 75;

    private static final int INSTALL_SHORTCUT_SUCCESSFUL = 0;
    private static final int INSTALL_SHORTCUT_IS_DUPLICATE = -1;
    private static final int INSTALL_SHORTCUT_NO_SPACE = -2;

    // A mime-type representing shortcut data
    public static final String SHORTCUT_MIMETYPE =
            "com.android.launcher/shortcut";

    private static Object sLock = new Object();

    private static void addToStringSet(SharedPreferences sharedPrefs,
            SharedPreferences.Editor editor, String key, String value) {
        Set<String> strings = sharedPrefs.getStringSet(key, null);
        if (strings == null) {
            strings = new HashSet<String>(0);
        } else {
            strings = new HashSet<String>(strings);
        }
        strings.add(value);
        editor.putStringSet(key, strings);
    }

    private static void addToInstallQueue(
            SharedPreferences sharedPrefs, PendingInstallShortcutInfo info) {
        synchronized(sLock) {
            try {
                JSONStringer json = new JSONStringer()
                    .object()
                    .key(DATA_INTENT_KEY).value(info.data.toUri(0))
                    .key(LAUNCH_INTENT_KEY).value(info.launchIntent.toUri(0))
                    .key(NAME_KEY).value(info.name);
                if (info.icon != null) {
                    byte[] iconByteArray = ItemInfo.flattenBitmap(info.icon);
                    json = json.key(ICON_KEY).value(
                        Base64.encodeToString(
                            iconByteArray, 0, iconByteArray.length, Base64.DEFAULT));
                }
                if (info.iconResource != null) {
                    json = json.key(ICON_RESOURCE_NAME_KEY).value(info.iconResource.resourceName);
                    json = json.key(ICON_RESOURCE_PACKAGE_NAME_KEY)
                        .value(info.iconResource.packageName);
                }
                json = json.endObject();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                addToStringSet(sharedPrefs, editor, APPS_PENDING_INSTALL, json.toString());
                editor.commit();
            } catch (org.json.JSONException e) {
                Log.d("InstallShortcutReceiver", "Exception when adding shortcut: " + e);
            }
        }
    }

    private static ArrayList<PendingInstallShortcutInfo> getAndClearInstallQueue(
            SharedPreferences sharedPrefs) {
        synchronized(sLock) {
            Set<String> strings = sharedPrefs.getStringSet(APPS_PENDING_INSTALL, null);
            if (strings == null) {
                return new ArrayList<PendingInstallShortcutInfo>();
            }
            ArrayList<PendingInstallShortcutInfo> infos =
                new ArrayList<PendingInstallShortcutInfo>();
            for (String json : strings) {
                try {
                    JSONObject object = (JSONObject) new JSONTokener(json).nextValue();
                    Intent data = Intent.parseUri(object.getString(DATA_INTENT_KEY), 0);
                    Intent launchIntent = Intent.parseUri(object.getString(LAUNCH_INTENT_KEY), 0);
                    String name = object.getString(NAME_KEY);
                    String iconBase64 = object.optString(ICON_KEY);
                    String iconResourceName = object.optString(ICON_RESOURCE_NAME_KEY);
                    String iconResourcePackageName =
                        object.optString(ICON_RESOURCE_PACKAGE_NAME_KEY);
                    if (iconBase64 != null && !iconBase64.isEmpty()) {
                        byte[] iconArray = Base64.decode(iconBase64, Base64.DEFAULT);
                        Bitmap b = BitmapFactory.decodeByteArray(iconArray, 0, iconArray.length);
                        data.putExtra(Intent.EXTRA_SHORTCUT_ICON, b);
                    } else if (iconResourceName != null && !iconResourceName.isEmpty()) {
                        Intent.ShortcutIconResource iconResource =
                            new Intent.ShortcutIconResource();
                        iconResource.resourceName = iconResourceName;
                        iconResource.packageName = iconResourcePackageName;
                        data.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
                    }
                    data.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
                    PendingInstallShortcutInfo info =
                        new PendingInstallShortcutInfo(data, name, launchIntent);
                    infos.add(info);
                } catch (org.json.JSONException e) {
                    Log.d("InstallShortcutReceiver", "Exception reading shortcut to add: " + e);
                } catch (java.net.URISyntaxException e) {
                    Log.d("InstallShortcutReceiver", "Exception reading shortcut to add: " + e);
                }
            }
            sharedPrefs.edit().putStringSet(APPS_PENDING_INSTALL, new HashSet<String>()).commit();
            return infos;
        }
    }

    // Determines whether to defer installing shortcuts immediately until
    // processAllPendingInstalls() is called.
    private static boolean mUseInstallQueue = false;

    private static class PendingInstallShortcutInfo {
        Intent data;
        Intent launchIntent;
        String name;
        Bitmap icon;
        Intent.ShortcutIconResource iconResource;

        public PendingInstallShortcutInfo(Intent rawData, String shortcutName,
                Intent shortcutIntent) {
            data = rawData;
            name = shortcutName;
            launchIntent = shortcutIntent;
        }
    }

    public void onReceive(Context context, Intent data) {
        if (!ACTION_INSTALL_SHORTCUT.equals(data.getAction())) {
            return;
        }

        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (intent == null) {
            return;
        }
        // This name is only used for comparisons and notifications, so fall back to activity name
        // if not supplied
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (name == null) {
            try {
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = pm.getActivityInfo(intent.getComponent(), 0);
                name = info.loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException nnfe) {
                return;
            }
        }
        Bitmap icon = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
        Intent.ShortcutIconResource iconResource =
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);

        // Queue the item up for adding if launcher has not loaded properly yet
        boolean launcherNotLoaded = LauncherModel.getCellCountX() <= 0 ||
                LauncherModel.getCellCountY() <= 0;

        PendingInstallShortcutInfo info = new PendingInstallShortcutInfo(data, name, intent);
        info.icon = icon;
        info.iconResource = iconResource;
        if (mUseInstallQueue || launcherNotLoaded) {
            String spKey = LauncherApplication.getSharedPreferencesKey();
            SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);
            addToInstallQueue(sp, info);
        } else {
            processInstallShortcut(context, info);
        }
    }

    static void enableInstallQueue() {
        mUseInstallQueue = true;
    }
    static void disableAndFlushInstallQueue(Context context) {
        mUseInstallQueue = false;
        flushInstallQueue(context);
    }
    static void flushInstallQueue(Context context) {
        String spKey = LauncherApplication.getSharedPreferencesKey();
        SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);
        ArrayList<PendingInstallShortcutInfo> installQueue = getAndClearInstallQueue(sp);
        Iterator<PendingInstallShortcutInfo> iter = installQueue.iterator();
        while (iter.hasNext()) {
            processInstallShortcut(context, iter.next());
        }
    }

    private static void processInstallShortcut(Context context,
            PendingInstallShortcutInfo pendingInfo) {
        String spKey = LauncherApplication.getSharedPreferencesKey();
        SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);

        final Intent data = pendingInfo.data;
        final Intent intent = pendingInfo.launchIntent;
        final String name = pendingInfo.name;

        // Lock on the app so that we don't try and get the items while apps are being added
        LauncherApplication app = (LauncherApplication) context.getApplicationContext();
        final int[] result = {INSTALL_SHORTCUT_SUCCESSFUL};
        boolean found = false;
        synchronized (app) {
            // Flush the LauncherModel worker thread, so that if we just did another
            // processInstallShortcut, we give it time for its shortcut to get added to the
            // database (getItemsInLocalCoordinates reads the database)
            app.getModel().flushWorkerThread();
            final ArrayList<ItemInfo> items = LauncherModel.getItemsInLocalCoordinates(context);
            final boolean exists = LauncherModel.shortcutExists(context, name, intent);

            // Try adding to the workspace screens incrementally, starting at the default or center
            // screen and alternating between +1, -1, +2, -2, etc. (using ~ ceil(i/2f)*(-1)^(i-1))
            final int screen = Launcher.DEFAULT_SCREEN;
            for (int i = 0; i < (2 * Launcher.SCREEN_COUNT) + 1 && !found; ++i) {
                int si = screen + (int) ((i / 2f) + 0.5f) * ((i % 2 == 1) ? 1 : -1);
                if (0 <= si && si < Launcher.SCREEN_COUNT) {
                    found = installShortcut(context, data, items, name, intent, si, exists, sp,
                            result);
                }
            }
        }

        // We only report error messages (duplicate shortcut or out of space) as the add-animation
        // will provide feedback otherwise
        if (!found) {
            if (result[0] == INSTALL_SHORTCUT_NO_SPACE) {
                Toast.makeText(context, context.getString(R.string.completely_out_of_space),
                        Toast.LENGTH_SHORT).show();
            } else if (result[0] == INSTALL_SHORTCUT_IS_DUPLICATE) {
                Toast.makeText(context, context.getString(R.string.shortcut_duplicate, name),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static boolean installShortcut(Context context, Intent data, ArrayList<ItemInfo> items,
            String name, final Intent intent, final int screen, boolean shortcutExists,
            final SharedPreferences sharedPrefs, int[] result) {
        int[] tmpCoordinates = new int[2];
        if (findEmptyCell(context, items, tmpCoordinates, screen)) {
            if (intent != null) {
                if (intent.getAction() == null) {
                    intent.setAction(Intent.ACTION_VIEW);
                } else if (intent.getAction().equals(Intent.ACTION_MAIN) &&
                        intent.getCategories() != null &&
                        intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                }

                // By default, we allow for duplicate entries (located in
                // different places)
                boolean duplicate = data.getBooleanExtra(Launcher.EXTRA_SHORTCUT_DUPLICATE, true);
                if (duplicate || !shortcutExists) {
                    new Thread("setNewAppsThread") {
                        public void run() {
                            synchronized (sLock) {
                                // If the new app is going to fall into the same page as before,
                                // then just continue adding to the current page
                                final int newAppsScreen = sharedPrefs.getInt(
                                        NEW_APPS_PAGE_KEY, screen);
                                SharedPreferences.Editor editor = sharedPrefs.edit();
                                if (newAppsScreen == -1 || newAppsScreen == screen) {
                                    addToStringSet(sharedPrefs,
                                        editor, NEW_APPS_LIST_KEY, intent.toUri(0));
                                }
                                editor.putInt(NEW_APPS_PAGE_KEY, screen);
                                editor.commit();
                            }
                        }
                    }.start();

                    // Update the Launcher db
                    LauncherApplication app = (LauncherApplication) context.getApplicationContext();
                    ShortcutInfo info = app.getModel().addShortcut(context, data,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, screen,
                            tmpCoordinates[0], tmpCoordinates[1], true);
                    if (info == null) {
                        return false;
                    }
                } else {
                    result[0] = INSTALL_SHORTCUT_IS_DUPLICATE;
                }

                return true;
            }
        } else {
            result[0] = INSTALL_SHORTCUT_NO_SPACE;
        }

        return false;
    }

    private static boolean findEmptyCell(Context context, ArrayList<ItemInfo> items, int[] xy,
            int screen) {
        final int xCount = LauncherModel.getCellCountX();
        final int yCount = LauncherModel.getCellCountY();
        boolean[][] occupied = new boolean[xCount][yCount];

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
                    for (int x = cellX; 0 <= x && x < cellX + spanX && x < xCount; x++) {
                        for (int y = cellY; 0 <= y && y < cellY + spanY && y < yCount; y++) {
                            occupied[x][y] = true;
                        }
                    }
                }
            }
        }

        return CellLayout.findVacantCell(xy, 1, 1, xCount, yCount, occupied);
    }
}
