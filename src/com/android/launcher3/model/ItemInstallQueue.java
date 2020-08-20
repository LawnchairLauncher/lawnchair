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

package com.android.launcher3.model;

import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID;
import static com.android.launcher3.model.data.AppInfo.makeLaunchIntent;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Preconditions;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Class to maintain a queue of pending items to be added to the workspace.
 */
public class ItemInstallQueue {

    public static final int FLAG_ACTIVITY_PAUSED = 1;
    public static final int FLAG_LOADER_RUNNING = 2;
    public static final int FLAG_DRAG_AND_DROP = 4;

    private static final String TAG = "InstallShortcutReceiver";
    private static final boolean DBG = false;

    // The set of shortcuts that are pending install
    private static final String APPS_PENDING_INSTALL = "apps_to_install";

    public static final int NEW_SHORTCUT_BOUNCE_DURATION = 450;
    public static final int NEW_SHORTCUT_STAGGER_DELAY = 85;

    public static MainThreadInitializedObject<ItemInstallQueue> INSTANCE =
            new MainThreadInitializedObject<>(ItemInstallQueue::new);

    private final Context mContext;

    // Determines whether to defer installing shortcuts immediately until
    // processAllPendingInstalls() is called.
    private int mInstallQueueDisabledFlags = 0;

    private ItemInstallQueue(Context context) {
        mContext = context;
    }

    @WorkerThread
    private void addToQueue(PendingInstallShortcutInfo info) {
        String encoded = info.encodeToString(mContext);
        SharedPreferences prefs = Utilities.getPrefs(mContext);
        Set<String> strings = prefs.getStringSet(APPS_PENDING_INSTALL, null);
        strings = (strings != null) ? new HashSet<>(strings) : new HashSet<>(1);
        strings.add(encoded);
        prefs.edit().putStringSet(APPS_PENDING_INSTALL, strings).apply();
    }

    @WorkerThread
    private void flushQueueInBackground() {
        Launcher launcher = Launcher.ACTIVITY_TRACKER.getCreatedActivity();
        if (launcher == null) {
            // Launcher not loaded
            return;
        }

        ArrayList<Pair<ItemInfo, Object>> installQueue = new ArrayList<>();
        SharedPreferences prefs = Utilities.getPrefs(mContext);
        Set<String> strings = prefs.getStringSet(APPS_PENDING_INSTALL, null);
        if (DBG) Log.d(TAG, "Getting and clearing APPS_PENDING_INSTALL: " + strings);
        if (strings == null) {
            return;
        }

        for (String encoded : strings) {
            PendingInstallShortcutInfo info = decode(encoded, mContext);
            if (info == null) {
                continue;
            }

            // Generate a shortcut info to add into the model
            installQueue.add(info.getItemInfo(mContext));
        }
        prefs.edit().remove(APPS_PENDING_INSTALL).apply();
        if (!installQueue.isEmpty()) {
            launcher.getModel().addAndBindAddedWorkspaceItems(installQueue);
        }
    }

    /**
     * Removes previously added items from the queue.
     */
    @WorkerThread
    public void removeFromInstallQueue(HashSet<String> packageNames, UserHandle user) {
        if (packageNames.isEmpty()) {
            return;
        }
        Preconditions.assertWorkerThread();

        SharedPreferences sp = Utilities.getPrefs(mContext);
        Set<String> strings = sp.getStringSet(APPS_PENDING_INSTALL, null);
        if (DBG) {
            Log.d(TAG, "APPS_PENDING_INSTALL: " + strings
                    + ", removing packages: " + packageNames);
        }
        if (strings == null || ((Collection) strings).isEmpty()) {
            return;
        }
        Set<String> newStrings = new HashSet<>(strings);
        Iterator<String> newStringsIter = newStrings.iterator();
        while (newStringsIter.hasNext()) {
            String encoded = newStringsIter.next();
            try {
                Decoder decoder = new Decoder(encoded, mContext);
                if (packageNames.contains(getIntentPackage(decoder.intent))
                        && user.equals(decoder.user)) {
                    newStringsIter.remove();
                }
            } catch (JSONException | URISyntaxException e) {
                Log.d(TAG, "Exception reading shortcut to add: " + e);
                newStringsIter.remove();
            }
        }
        sp.edit().putStringSet(APPS_PENDING_INSTALL, newStrings).apply();
    }

    /**
     * Adds an item to the install queue
     */
    public void queueItem(ShortcutInfo info) {
        queuePendingShortcutInfo(new PendingInstallShortcutInfo(info));
    }

    /**
     * Adds an item to the install queue
     */
    public void queueItem(AppWidgetProviderInfo info, int widgetId) {
        queuePendingShortcutInfo(new PendingInstallShortcutInfo(info, widgetId));
    }

    /**
     * Adds an item to the install queue
     */
    public void queueItem(String packageName, UserHandle userHandle) {
        queuePendingShortcutInfo(new PendingInstallShortcutInfo(packageName, userHandle));
    }

    /**
     * Returns all pending shorts in the queue
     */
    @WorkerThread
    public HashSet<ShortcutKey> getPendingShortcuts() {
        HashSet<ShortcutKey> result = new HashSet<>();

        Set<String> strings = Utilities.getPrefs(mContext).getStringSet(APPS_PENDING_INSTALL, null);
        if (strings == null || ((Collection) strings).isEmpty()) {
            return result;
        }

        for (String encoded : strings) {
            try {
                Decoder decoder = new Decoder(encoded, mContext);
                if (decoder.optInt(Favorites.ITEM_TYPE, -1) == ITEM_TYPE_DEEP_SHORTCUT) {
                    result.add(ShortcutKey.fromIntent(decoder.intent, decoder.user));
                }
            } catch (JSONException | URISyntaxException e) {
                Log.d(TAG, "Exception reading shortcut to add: " + e);
            }
        }
        return result;
    }

    private void queuePendingShortcutInfo(PendingInstallShortcutInfo info) {
        // Queue the item up for adding if launcher has not loaded properly yet
        MODEL_EXECUTOR.post(() -> addToQueue(info));
        flushInstallQueue();
    }

    /**
     * Pauses the push-to-model flow until unpaused. All items are held in the queue and
     * not added to the model.
     */
    public void pauseModelPush(int flag) {
        mInstallQueueDisabledFlags |= flag;
    }

    /**
     * Adds all the queue items to the model if the use is completely resumed.
     */
    public void resumeModelPush(int flag) {
        mInstallQueueDisabledFlags &= ~flag;
        flushInstallQueue();
    }

    private void flushInstallQueue() {
        if (mInstallQueueDisabledFlags != 0) {
            return;
        }
        MODEL_EXECUTOR.post(this::flushQueueInBackground);
    }

    private static class PendingInstallShortcutInfo extends ItemInfo {

        final Intent intent;

        @Nullable ShortcutInfo shortcutInfo;
        @Nullable AppWidgetProviderInfo providerInfo;

        /**
         * Initializes a PendingInstallShortcutInfo to represent a pending launcher target.
         */
        public PendingInstallShortcutInfo(String packageName, UserHandle userHandle) {
            itemType = Favorites.ITEM_TYPE_APPLICATION;
            intent = new Intent().setPackage(packageName);
            user = userHandle;
        }

        /**
         * Initializes a PendingInstallShortcutInfo to represent a deep shortcut.
         */
        public PendingInstallShortcutInfo(ShortcutInfo info) {
            itemType = Favorites.ITEM_TYPE_DEEP_SHORTCUT;
            intent = ShortcutKey.makeIntent(info);
            user = info.getUserHandle();

            shortcutInfo = info;
        }

        /**
         * Initializes a PendingInstallShortcutInfo to represent an app widget.
         */
        public PendingInstallShortcutInfo(AppWidgetProviderInfo info, int widgetId) {
            itemType = Favorites.ITEM_TYPE_APPWIDGET;
            intent = new Intent()
                    .setComponent(info.provider)
                    .putExtra(EXTRA_APPWIDGET_ID, widgetId);
            user = info.getProfile();

            providerInfo = info;
        }

        public String encodeToString(Context context) {
            try {
                return new JSONStringer()
                        .object()
                        .key(Favorites.ITEM_TYPE).value(itemType)
                        .key(Favorites.INTENT).value(intent.toUri(0))
                        .key(PROFILE_ID).value(
                                UserCache.INSTANCE.get(context).getSerialNumberForUser(user))
                        .endObject().toString();
            } catch (JSONException e) {
                Log.d(TAG, "Exception when adding shortcut: " + e);
                return null;
            }
        }

        public Pair<ItemInfo, Object> getItemInfo(Context context) {
            switch (itemType) {
                case ITEM_TYPE_APPLICATION: {
                    String packageName = intent.getPackage();
                    List<LauncherActivityInfo> laiList =
                            context.getSystemService(LauncherApps.class)
                                    .getActivityList(packageName, user);

                    final WorkspaceItemInfo si = new WorkspaceItemInfo();
                    si.user = user;
                    si.itemType = ITEM_TYPE_APPLICATION;

                    LauncherActivityInfo lai;
                    boolean usePackageIcon = laiList.isEmpty();
                    if (usePackageIcon) {
                        lai = null;
                        si.intent = makeLaunchIntent(new ComponentName(packageName, ""))
                                .setPackage(packageName);
                        si.status |= WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON;
                    } else {
                        lai = laiList.get(0);
                        si.intent = makeLaunchIntent(lai);
                    }
                    LauncherAppState.getInstance(context).getIconCache()
                            .getTitleAndIcon(si, () -> lai, usePackageIcon, false);
                    return Pair.create(si, null);
                }
                case ITEM_TYPE_DEEP_SHORTCUT: {
                    WorkspaceItemInfo itemInfo = new WorkspaceItemInfo(shortcutInfo, context);
                    LauncherAppState.getInstance(context).getIconCache()
                            .getShortcutIcon(itemInfo, shortcutInfo);
                    return Pair.create(itemInfo, shortcutInfo);
                }
                case ITEM_TYPE_APPWIDGET: {
                    LauncherAppWidgetProviderInfo info = LauncherAppWidgetProviderInfo
                            .fromProviderInfo(context, providerInfo);
                    LauncherAppWidgetInfo widgetInfo = new LauncherAppWidgetInfo(
                            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0),
                            info.provider);
                    InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
                    widgetInfo.minSpanX = info.minSpanX;
                    widgetInfo.minSpanY = info.minSpanY;
                    widgetInfo.spanX = Math.min(info.spanX, idp.numColumns);
                    widgetInfo.spanY = Math.min(info.spanY, idp.numRows);
                    widgetInfo.user = user;
                    return Pair.create(widgetInfo, providerInfo);
                }
            }
            return null;
        }
    }

    private static String getIntentPackage(Intent intent) {
        return intent.getComponent() == null
                ? intent.getPackage() : intent.getComponent().getPackageName();
    }

    private static PendingInstallShortcutInfo decode(String encoded, Context context) {
        try {
            Decoder decoder = new Decoder(encoded, context);
            switch (decoder.optInt(Favorites.ITEM_TYPE, -1)) {
                case Favorites.ITEM_TYPE_APPLICATION:
                    return new PendingInstallShortcutInfo(
                            decoder.intent.getPackage(), decoder.user);
                case Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                    List<ShortcutInfo> si = ShortcutKey.fromIntent(decoder.intent, decoder.user)
                            .buildRequest(context)
                            .query(ShortcutRequest.ALL);
                    if (si.isEmpty()) {
                        return null;
                    } else {
                        return new PendingInstallShortcutInfo(si.get(0));
                    }
                }
                case Favorites.ITEM_TYPE_APPWIDGET: {
                    int widgetId = decoder.intent.getIntExtra(EXTRA_APPWIDGET_ID, 0);
                    AppWidgetProviderInfo info =
                            AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId);
                    if (info == null || !info.provider.equals(decoder.intent.getComponent())
                            || !info.getProfile().equals(decoder.user)) {
                        return null;
                    }
                    return new PendingInstallShortcutInfo(info, widgetId);
                }
                default:
                    Log.e(TAG, "Unknown item type");
            }
        } catch (JSONException | URISyntaxException e) {
            Log.d(TAG, "Exception reading shortcut to add: " + e);
        }
        return null;
    }

    private static class Decoder extends JSONObject {
        public final Intent intent;
        public final UserHandle user;

        private Decoder(String encoded, Context context) throws JSONException, URISyntaxException {
            super(encoded);
            intent = Intent.parseUri(getString(Favorites.INTENT), 0);
            user = has(PROFILE_ID)
                    ? UserCache.INSTANCE.get(context).getUserForSerialNumber(getLong(PROFILE_ID))
                    : Process.myUserHandle();
            if (user == null || intent == null) {
                throw new JSONException("Invalid data");
            }
        }
    }
}
