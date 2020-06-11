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
package com.android.launcher3.model;

import static com.android.launcher3.model.WidgetsModel.GO_DISABLE_WIDGETS;
import static com.android.launcher3.shortcuts.ShortcutRequest.PINNED;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.MutableInt;

import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.PromiseAppInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.widget.WidgetListRowEntry;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * All the data stored in-memory and managed by the LauncherModel
 */
public class BgDataModel {

    private static final String TAG = "BgDataModel";

    /**
     * Map of all the ItemInfos (shortcuts, folders, and widgets) created by
     * LauncherModel to their ids
     */
    public final IntSparseArrayMap<ItemInfo> itemsIdMap = new IntSparseArrayMap<>();

    /**
     * List of all the folders and shortcuts directly on the home screen (no widgets
     * or shortcuts within folders).
     */
    public final ArrayList<ItemInfo> workspaceItems = new ArrayList<>();

    /**
     * All LauncherAppWidgetInfo created by LauncherModel.
     */
    public final ArrayList<LauncherAppWidgetInfo> appWidgets = new ArrayList<>();

    /**
     * Map of id to FolderInfos of all the folders created by LauncherModel
     */
    public final IntSparseArrayMap<FolderInfo> folders = new IntSparseArrayMap<>();

    /**
     * Map of ShortcutKey to the number of times it is pinned.
     */
    public final Map<ShortcutKey, MutableInt> pinnedShortcutCounts = new HashMap<>();

    /**
     * List of all cached predicted items visible on home screen
     */
    public final ArrayList<AppInfo> cachedPredictedItems = new ArrayList<>();

    /**
     * @see Callbacks#FLAG_HAS_SHORTCUT_PERMISSION
     * @see Callbacks#FLAG_QUIET_MODE_ENABLED
     * @see Callbacks#FLAG_QUIET_MODE_CHANGE_PERMISSION
     */
    public int flags;

    /**
     * Maps all launcher activities to counts of their shortcuts.
     */
    public final HashMap<ComponentKey, Integer> deepShortcutMap = new HashMap<>();

    /**
     * Entire list of widgets.
     */
    public final WidgetsModel widgetsModel = new WidgetsModel();

    /**
     * Id when the model was last bound
     */
    public int lastBindId = 0;

    /**
     * Clears all the data
     */
    public synchronized void clear() {
        workspaceItems.clear();
        appWidgets.clear();
        folders.clear();
        itemsIdMap.clear();
        pinnedShortcutCounts.clear();
        deepShortcutMap.clear();
    }

    /**
     * Creates an array of valid workspace screens based on current items in the model.
     */
    public synchronized IntArray collectWorkspaceScreens() {
        IntSet screenSet = new IntSet();
        for (ItemInfo item: itemsIdMap) {
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                screenSet.add(item.screenId);
            }
        }
        if (FeatureFlags.QSB_ON_FIRST_SCREEN || screenSet.isEmpty()) {
            screenSet.add(Workspace.FIRST_SCREEN_ID);
        }
        return screenSet.getArray();
    }

    public synchronized void dump(String prefix, FileDescriptor fd, PrintWriter writer,
            String[] args) {
        writer.println(prefix + "Data Model:");
        writer.println(prefix + " ---- workspace items ");
        for (int i = 0; i < workspaceItems.size(); i++) {
            writer.println(prefix + '\t' + workspaceItems.get(i).toString());
        }
        writer.println(prefix + " ---- appwidget items ");
        for (int i = 0; i < appWidgets.size(); i++) {
            writer.println(prefix + '\t' + appWidgets.get(i).toString());
        }
        writer.println(prefix + " ---- folder items ");
        for (int i = 0; i< folders.size(); i++) {
            writer.println(prefix + '\t' + folders.valueAt(i).toString());
        }
        writer.println(prefix + " ---- items id map ");
        for (int i = 0; i< itemsIdMap.size(); i++) {
            writer.println(prefix + '\t' + itemsIdMap.valueAt(i).toString());
        }

        if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "shortcut counts ");
            for (Integer count : deepShortcutMap.values()) {
                writer.print(count + ", ");
            }
            writer.println();
        }
    }

    public synchronized void removeItem(Context context, ItemInfo... items) {
        removeItem(context, Arrays.asList(items));
    }

    public synchronized void removeItem(Context context, Iterable<? extends ItemInfo> items) {
        for (ItemInfo item : items) {
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                    folders.remove(item.id);
                    if (FeatureFlags.IS_STUDIO_BUILD) {
                        for (ItemInfo info : itemsIdMap) {
                            if (info.container == item.id) {
                                // We are deleting a folder which still contains items that
                                // think they are contained by that folder.
                                String msg = "deleting a folder (" + item + ") which still " +
                                        "contains items (" + info + ")";
                                Log.e(TAG, msg);
                            }
                        }
                    }
                    workspaceItems.remove(item);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                    // Decrement pinned shortcut count
                    ShortcutKey pinnedShortcut = ShortcutKey.fromItemInfo(item);
                    MutableInt count = pinnedShortcutCounts.get(pinnedShortcut);
                    if ((count == null || --count.value == 0)
                            && !InstallShortcutReceiver.getPendingShortcuts(context)
                                .contains(pinnedShortcut)) {
                        unpinShortcut(context, pinnedShortcut);
                    }
                    // Fall through.
                }
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    workspaceItems.remove(item);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                    appWidgets.remove(item);
                    break;
            }
            itemsIdMap.remove(item.id);
        }
    }

    public synchronized void addItem(Context context, ItemInfo item, boolean newItem) {
        itemsIdMap.put(item.id, item);
        switch (item.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                folders.put(item.id, (FolderInfo) item);
                workspaceItems.add(item);
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                // Increment the count for the given shortcut
                ShortcutKey pinnedShortcut = ShortcutKey.fromItemInfo(item);
                MutableInt count = pinnedShortcutCounts.get(pinnedShortcut);
                if (count == null) {
                    count = new MutableInt(1);
                    pinnedShortcutCounts.put(pinnedShortcut, count);
                } else {
                    count.value++;
                }

                // Since this is a new item, pin the shortcut in the system server.
                if (newItem && count.value == 1) {
                    updatePinnedShortcuts(context, pinnedShortcut, List::add);
                }
                // Fall through
            }
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                        item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    workspaceItems.add(item);
                } else {
                    if (newItem) {
                        if (!folders.containsKey(item.container)) {
                            // Adding an item to a folder that doesn't exist.
                            String msg = "adding item: " + item + " to a folder that " +
                                    " doesn't exist";
                            Log.e(TAG, msg);
                        }
                    } else {
                        findOrMakeFolder(item.container).add((WorkspaceItemInfo) item, false);
                    }

                }
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
            case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                appWidgets.add((LauncherAppWidgetInfo) item);
                break;
        }
    }

    /**
     * Removes the given shortcut from the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    public void unpinShortcut(Context context, ShortcutKey key) {
        updatePinnedShortcuts(context, key, List::remove);
    }

    private void updatePinnedShortcuts(Context context, ShortcutKey key,
            BiConsumer<List<String>, String> idOp) {
        if (GO_DISABLE_WIDGETS) {
            return;
        }
        String packageName = key.componentName.getPackageName();
        String id = key.getId();
        UserHandle user = key.user;
        List<String> pinnedIds = new ShortcutRequest(context, user)
                .forPackage(packageName)
                .query(PINNED)
                .stream()
                .map(ShortcutInfo::getId)
                .collect(Collectors.toCollection(ArrayList::new));
        idOp.accept(pinnedIds, id);
        try {
            context.getSystemService(LauncherApps.class).pinShortcuts(packageName, pinnedIds, user);
        } catch (SecurityException | IllegalStateException e) {
            Log.w(TAG, "Failed to pin shortcut", e);
        }
    }

    /**
     * Return an existing FolderInfo object if we have encountered this ID previously,
     * or make a new one.
     */
    public synchronized FolderInfo findOrMakeFolder(int id) {
        // See if a placeholder was created for us already
        FolderInfo folderInfo = folders.get(id);
        if (folderInfo == null) {
            // No placeholder -- create a new instance
            folderInfo = new FolderInfo();
            folders.put(id, folderInfo);
        }
        return folderInfo;
    }

    /**
     * Clear all the deep shortcut counts for the given package, and re-add the new shortcut counts.
     */
    public synchronized void updateDeepShortcutCounts(
            String packageName, UserHandle user, List<ShortcutInfo> shortcuts) {
        if (packageName != null) {
            Iterator<ComponentKey> keysIter = deepShortcutMap.keySet().iterator();
            while (keysIter.hasNext()) {
                ComponentKey next = keysIter.next();
                if (next.componentName.getPackageName().equals(packageName)
                        && next.user.equals(user)) {
                    keysIter.remove();
                }
            }
        }

        // Now add the new shortcuts to the map.
        for (ShortcutInfo shortcut : shortcuts) {
            boolean shouldShowInContainer = shortcut.isEnabled()
                    && (shortcut.isDeclaredInManifest() || shortcut.isDynamic())
                    && shortcut.getActivity() != null;
            if (shouldShowInContainer) {
                ComponentKey targetComponent
                        = new ComponentKey(shortcut.getActivity(), shortcut.getUserHandle());

                Integer previousCount = deepShortcutMap.get(targetComponent);
                deepShortcutMap.put(targetComponent, previousCount == null ? 1 : previousCount + 1);
            }
        }
    }

    public interface Callbacks {
        // If the launcher has permission to access deep shortcuts.
        int FLAG_HAS_SHORTCUT_PERMISSION = 1 << 0;
        // If quiet mode is enabled for any user
        int FLAG_QUIET_MODE_ENABLED = 1 << 1;
        // If launcher can change quiet mode
        int FLAG_QUIET_MODE_CHANGE_PERMISSION = 1 << 2;

        /**
         * Returns the page number to bind first, synchronously if possible or -1
         */
        int getPageToBindSynchronously();
        void clearPendingBinds();
        void startBinding();
        void bindItems(List<ItemInfo> shortcuts, boolean forceAnimateIcons);
        void bindScreens(IntArray orderedScreenIds);
        void finishFirstPageBind(ViewOnDrawExecutor executor);
        void finishBindingItems(int pageBoundFirst);
        void preAddApps();
        void bindAppsAdded(IntArray newScreens,
                ArrayList<ItemInfo> addNotAnimated, ArrayList<ItemInfo> addAnimated);
        void bindPromiseAppProgressUpdated(PromiseAppInfo app);
        void bindWorkspaceItemsChanged(ArrayList<WorkspaceItemInfo> updated);
        void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets);
        void bindRestoreItemsChange(HashSet<ItemInfo> updates);
        void bindWorkspaceComponentsRemoved(ItemInfoMatcher matcher);
        void bindAllWidgets(ArrayList<WidgetListRowEntry> widgets);
        void onPageBoundSynchronously(int page);
        void executeOnNextDraw(ViewOnDrawExecutor executor);
        void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMap);

        void bindAllApplications(AppInfo[] apps, int flags);

        /**
         * Binds predicted appInfos at at available prediction slots.
         */
        void bindPredictedItems(List<AppInfo> appInfos, IntArray ranks);
    }
}
