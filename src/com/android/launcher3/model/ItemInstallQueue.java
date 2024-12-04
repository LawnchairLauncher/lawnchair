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
import static com.android.launcher3.model.data.AppInfo.makeLaunchIntent;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_ARCHIVED;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.PersistedItemArray;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class to maintain a queue of pending items to be added to the workspace.
 */
public class ItemInstallQueue implements SafeCloseable {

    private static final String LOG = "ItemInstallQueue";

    public static final int FLAG_ACTIVITY_PAUSED = 1;
    public static final int FLAG_LOADER_RUNNING = 2;
    public static final int FLAG_DRAG_AND_DROP = 4;

    private static final String TAG = "InstallShortcutReceiver";

    // The set of shortcuts that are pending install
    private static final String APPS_PENDING_INSTALL = "apps_to_install";

    public static final int NEW_SHORTCUT_BOUNCE_DURATION = 450;
    public static final int NEW_SHORTCUT_STAGGER_DELAY = 85;

    public static MainThreadInitializedObject<ItemInstallQueue> INSTANCE =
            new MainThreadInitializedObject<>(ItemInstallQueue::new);

    private final PersistedItemArray<PendingInstallShortcutInfo> mStorage =
            new PersistedItemArray<>(APPS_PENDING_INSTALL);
    private final Context mContext;

    // Determines whether to defer installing shortcuts immediately until
    // processAllPendingInstalls() is called.
    private int mInstallQueueDisabledFlags = 0;

    // Only accessed on worker thread
    private List<PendingInstallShortcutInfo> mItems;

    private ItemInstallQueue(Context context) {
        mContext = context;
    }

    @Override
    public void close() {}

    @WorkerThread
    private void ensureQueueLoaded() {
        Preconditions.assertWorkerThread();
        if (mItems == null) {
            mItems = mStorage.read(mContext, this::decode);
        }
    }

    @WorkerThread
    private void addToQueue(PendingInstallShortcutInfo info) {
        ensureQueueLoaded();
        if (!mItems.contains(info)) {
            mItems.add(info);
            mStorage.write(mContext, mItems);
        }
    }

    @WorkerThread
    private void flushQueueInBackground() {
        Launcher launcher = Launcher.ACTIVITY_TRACKER.getCreatedActivity();
        if (launcher == null) {
            // Launcher not loaded
            return;
        }
        ensureQueueLoaded();
        if (mItems.isEmpty()) {
            return;
        }

        List<Pair<ItemInfo, Object>> installQueue = mItems.stream()
                .map(info -> info.getItemInfo(mContext))
                .collect(Collectors.toList());

        // Add the items and clear queue
        if (!installQueue.isEmpty()) {
            // add log
            launcher.getModel().addAndBindAddedWorkspaceItems(installQueue);
        }
        mItems.clear();
        mStorage.getFile(mContext).delete();
    }

    /**
     * Removes previously added items from the queue.
     */
    @WorkerThread
    public void removeFromInstallQueue(HashSet<String> packageNames, UserHandle user) {
        if (packageNames.isEmpty()) {
            return;
        }
        ensureQueueLoaded();
        if (mItems.removeIf(item ->
                item.user.equals(user) && packageNames.contains(getIntentPackage(item.intent)))) {
            mStorage.write(mContext, mItems);
        }
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
     * Returns a stream of all pending shortcuts in the queue
     */
    @WorkerThread
    public Stream<ShortcutKey> getPendingShortcuts(UserHandle user) {
        ensureQueueLoaded();
        return mItems.stream()
                .filter(item -> item.itemType == ITEM_TYPE_DEEP_SHORTCUT && user.equals(item.user))
                .map(item -> ShortcutKey.fromIntent(item.intent, user));
    }

    private void queuePendingShortcutInfo(PendingInstallShortcutInfo info) {
        final Exception stackTrace = new Exception();

        // Queue the item up for adding if launcher has not loaded properly yet
        MODEL_EXECUTOR.post(() -> {
            Pair<ItemInfo, Object> itemInfo = info.getItemInfo(mContext);
            if (itemInfo == null) {
                FileLog.d(LOG,
                        "Adding PendingInstallShortcutInfo with no attached info to queue.",
                        stackTrace);
            } else {
                FileLog.d(LOG,
                        "Adding PendingInstallShortcutInfo to queue. Attached info: "
                                + itemInfo.first,
                        stackTrace);
            }

            addToQueue(info);
        });
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

        @Override
        @Nullable
        public Intent getIntent() {
            return intent;
        }

        @SuppressWarnings("NewApi")
        public Pair<ItemInfo, Object> getItemInfo(Context context) {
            switch (itemType) {
                case ITEM_TYPE_APPLICATION: {
                    String packageName = intent.getPackage();
                    List<LauncherActivityInfo> laiList =
                            context.getSystemService(LauncherApps.class)
                                    .getActivityList(packageName, user);

                    final WorkspaceItemInfo si = new WorkspaceItemInfo();
                    si.user = user;

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
                        if (Flags.enableSupportForArchiving()
                                && lai.getActivityInfo().isArchived) {
                            si.runtimeStatusFlags |= FLAG_ARCHIVED;
                        }
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

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PendingInstallShortcutInfo) {
                PendingInstallShortcutInfo other = (PendingInstallShortcutInfo) obj;

                boolean userMatches = user.equals(other.user);
                boolean itemTypeMatches = itemType == other.itemType;
                boolean intentMatches = intent.toUri(0).equals(other.intent.toUri(0));
                boolean shortcutInfoMatches = shortcutInfo == null
                        ? other.shortcutInfo == null
                        : other.shortcutInfo != null
                            && shortcutInfo.getId().equals(other.shortcutInfo.getId())
                            && shortcutInfo.getPackage().equals(other.shortcutInfo.getPackage());
                boolean providerInfoMatches = providerInfo == null
                        ? other.providerInfo == null
                        : other.providerInfo != null
                            && providerInfo.provider.equals(other.providerInfo.provider);

                return userMatches
                        && itemTypeMatches
                        && intentMatches
                        && shortcutInfoMatches
                        && providerInfoMatches;
            }
            return false;
        }
    }

    private static String getIntentPackage(Intent intent) {
        return intent.getComponent() == null
                ? intent.getPackage() : intent.getComponent().getPackageName();
    }

    private PendingInstallShortcutInfo decode(int itemType, UserHandle user, Intent intent) {
        switch (itemType) {
            case Favorites.ITEM_TYPE_APPLICATION:
                return new PendingInstallShortcutInfo(intent.getPackage(), user);
            case Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                List<ShortcutInfo> si = ShortcutKey.fromIntent(intent, user)
                        .buildRequest(mContext)
                        .query(ShortcutRequest.ALL);
                if (si.isEmpty()) {
                    return null;
                } else {
                    return new PendingInstallShortcutInfo(si.get(0));
                }
            }
            case Favorites.ITEM_TYPE_APPWIDGET: {
                int widgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, 0);
                AppWidgetProviderInfo info =
                        AppWidgetManager.getInstance(mContext).getAppWidgetInfo(widgetId);
                if (info == null || !info.provider.equals(intent.getComponent())
                        || !info.getProfile().equals(user)) {
                    return null;
                }
                return new PendingInstallShortcutInfo(info, widgetId);
            }
            default:
                Log.e(TAG, "Unknown item type");
        }
        return null;
    }
}
