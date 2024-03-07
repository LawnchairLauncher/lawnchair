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

import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY;

import static com.android.launcher3.BuildConfig.QSB_ON_FIRST_SCREEN;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SMARTSPACE_REMOVAL;
import static com.android.launcher3.config.FeatureFlags.shouldShowFirstPageWidget;
import static com.android.launcher3.model.WidgetsModel.GO_DISABLE_WIDGETS;
import static com.android.launcher3.shortcuts.ShortcutRequest.PINNED;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * Extra container based items
     */
    public final IntSparseArrayMap<FixedContainerItems> extraItems = new IntSparseArrayMap<>();

    /**
     * Maps all launcher activities to counts of their shortcuts.
     */
    public final HashMap<ComponentKey, Integer> deepShortcutMap = new HashMap<>();

    /**
     * Entire list of widgets.
     */
    public final WidgetsModel widgetsModel = new WidgetsModel();

    /**
     * Cache for strings used in launcher
     */
    public final StringCache stringCache = new StringCache();

    /**
     * Id when the model was last bound
     */
    public int lastBindId = 0;

    /**
     * Load id for which the callbacks were successfully bound
     */
    public int lastLoadId = -1;
    public boolean isFirstPagePinnedItemEnabled = QSB_ON_FIRST_SCREEN
            && !ENABLE_SMARTSPACE_REMOVAL.get();

    /**
     * Clears all the data
     */
    public synchronized void clear() {
        workspaceItems.clear();
        appWidgets.clear();
        folders.clear();
        itemsIdMap.clear();
        deepShortcutMap.clear();
        extraItems.clear();
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
        if ((FeatureFlags.QSB_ON_FIRST_SCREEN
                && !shouldShowFirstPageWidget())
                || screenSet.isEmpty()) {
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
        for (int i = 0; i < folders.size(); i++) {
            writer.println(prefix + '\t' + folders.valueAt(i).toString());
        }
        writer.println(prefix + " ---- extra items ");
        for (int i = 0; i < extraItems.size(); i++) {
            writer.println(prefix + '\t' + extraItems.valueAt(i).toString());
        }
        writer.println(prefix + " ---- items id map ");
        for (int i = 0; i < itemsIdMap.size(); i++) {
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
        ArraySet<UserHandle> updatedDeepShortcuts = new ArraySet<>();
        for (ItemInfo item : items) {
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR:
                    folders.remove(item.id);
                    if (FeatureFlags.IS_STUDIO_BUILD) {
                        for (ItemInfo info : itemsIdMap) {
                            if (info.container == item.id) {
                                // We are deleting a folder which still contains items that
                                // think they are contained by that folder.
                                String msg = "deleting a collection (" + item + ") which still "
                                        + "contains items (" + info + ")";
                                Log.e(TAG, msg);
                            }
                        }
                    }
                    workspaceItems.remove(item);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                    updatedDeepShortcuts.add(item.user);
                    // Fall through.
                }
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                    workspaceItems.remove(item);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                    appWidgets.remove(item);
                    break;
            }
            itemsIdMap.remove(item.id);
        }
        updatedDeepShortcuts.forEach(user -> updateShortcutPinnedState(context, user));
    }

    public synchronized void addItem(Context context, ItemInfo item, boolean newItem) {
        addItem(context, item, newItem, null);
    }

    public synchronized void addItem(
            Context context, ItemInfo item, boolean newItem, @Nullable LoaderMemoryLogger logger) {
        if (logger != null) {
            logger.addLog(
                    Log.DEBUG,
                    TAG,
                    String.format("Adding item to ID map: %s", item.toString()),
                    /* stackTrace= */ null);
        }
        itemsIdMap.put(item.id, item);
        switch (item.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
            case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR:
                folders.put(item.id, (FolderInfo) item);
                workspaceItems.add(item);
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                        item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    workspaceItems.add(item);
                } else {
                    if (newItem) {
                        if (!folders.containsKey(item.container)) {
                            // Adding an item to a nonexistent collection.
                            String msg = "attempted to add item: " + item + " to a nonexistent app"
                                    + " collection";
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
        if (newItem && item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            updateShortcutPinnedState(context, item.user);
        }
    }

    /**
     * Updates the deep shortucts state in system to match out internal model, pinning any missing
     * shortcuts and unpinning any extra shortcuts.
     */
    public void updateShortcutPinnedState(Context context) {
        for (UserHandle user : UserCache.INSTANCE.get(context).getUserProfiles()) {
            updateShortcutPinnedState(context, user);
        }
    }

    /**
     * Updates the deep shortucts state in system to match out internal model, pinning any missing
     * shortcuts and unpinning any extra shortcuts.
     */
    public synchronized void updateShortcutPinnedState(Context context, UserHandle user) {
        if (GO_DISABLE_WIDGETS) {
            return;
        }

        // Collect all system shortcuts
        QueryResult result = new ShortcutRequest(context, user)
                .query(PINNED | FLAG_GET_KEY_FIELDS_ONLY);
        if (!result.wasSuccess()) {
            return;
        }
        // Map of packageName to shortcutIds that are currently in the system
        Map<String, Set<String>> systemMap = result.stream()
                .collect(groupingBy(ShortcutInfo::getPackage,
                        mapping(ShortcutInfo::getId, Collectors.toSet())));

        // Collect all model shortcuts
        Stream.Builder<WorkspaceItemInfo> itemStream = Stream.builder();
        forAllWorkspaceItemInfos(user, itemStream::accept);
        // Map of packageName to shortcutIds that are currently in our model
        Map<String, Set<String>> modelMap = Stream.concat(
                    // Model shortcuts
                    itemStream.build()
                        .filter(wi -> wi.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                        .map(ShortcutKey::fromItemInfo),
                    // Pending shortcuts
                    ItemInstallQueue.INSTANCE.get(context).getPendingShortcuts(user))
                .collect(groupingBy(ShortcutKey::getPackageName,
                        mapping(ShortcutKey::getId, Collectors.toSet())));

        // Check for diff
        for (Map.Entry<String, Set<String>> entry : modelMap.entrySet()) {
            Set<String> modelShortcuts = entry.getValue();
            Set<String> systemShortcuts = systemMap.remove(entry.getKey());
            if (systemShortcuts == null) {
                systemShortcuts = Collections.emptySet();
            }

            // Do not use .equals as it can vary based on the type of set
            if (systemShortcuts.size() != modelShortcuts.size()
                    || !systemShortcuts.containsAll(modelShortcuts)) {
                // Update system state for this package
                try {
                    context.getSystemService(LauncherApps.class).pinShortcuts(
                            entry.getKey(), new ArrayList<>(modelShortcuts), user);
                } catch (SecurityException | IllegalStateException e) {
                    Log.w(TAG, "Failed to pin shortcut", e);
                }
            }
        }

        // If there are any extra pinned shortcuts, remove them
        systemMap.keySet().forEach(packageName -> {
            // Update system state
            try {
                context.getSystemService(LauncherApps.class).pinShortcuts(
                        packageName, Collections.emptyList(), user);
            } catch (SecurityException | IllegalStateException e) {
                Log.w(TAG, "Failed to unpin shortcut", e);
            }
        });
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

    /**
     * Returns a list containing all workspace items including widgets.
     */
    public synchronized ArrayList<ItemInfo> getAllWorkspaceItems() {
        ArrayList<ItemInfo> items = new ArrayList<>(workspaceItems.size() + appWidgets.size());
        items.addAll(workspaceItems);
        items.addAll(appWidgets);
        return items;
    }

    /**
     * Calls the provided {@code op} for all workspaceItems in the in-memory model (both persisted
     * items and dynamic/predicted items for the provided {@code userHandle}.
     * Note the call is not synchronized over the model, that should be handled by the called.
     */
    public void forAllWorkspaceItemInfos(UserHandle userHandle, Consumer<WorkspaceItemInfo> op) {
        for (ItemInfo info : itemsIdMap) {
            if (info instanceof WorkspaceItemInfo && userHandle.equals(info.user)) {
                op.accept((WorkspaceItemInfo) info);
            }
        }

        for (int i = extraItems.size() - 1; i >= 0; i--) {
            for (ItemInfo info : extraItems.valueAt(i).items) {
                if (info instanceof WorkspaceItemInfo && userHandle.equals(info.user)) {
                    op.accept((WorkspaceItemInfo) info);
                }
            }
        }
    }

    /**
     * An object containing items corresponding to a fixed container
     */
    public static class FixedContainerItems {

        public final int containerId;
        public final List<ItemInfo> items;

        public FixedContainerItems(int containerId, List<ItemInfo> items) {
            this.containerId = containerId;
            this.items = Collections.unmodifiableList(items);
        }

        @Override
        @NonNull
        public final String toString() {
            StringBuilder s = new StringBuilder();
            s.append("FixedContainerItems:");
            s.append(" id=").append(containerId);
            s.append(" itemCount=").append(items.size());
            for (int i = 0; i < items.size(); i++) {
                s.append(" item #").append(i).append(": ").append(items.get(i).toString());
            }
            return s.toString();
        }

    }


    public interface Callbacks {
        // If the launcher has permission to access deep shortcuts.
        int FLAG_HAS_SHORTCUT_PERMISSION = 1 << 0;
        // If quiet mode is enabled for any user
        int FLAG_QUIET_MODE_ENABLED = 1 << 1;
        // If launcher can change quiet mode
        int FLAG_QUIET_MODE_CHANGE_PERMISSION = 1 << 2;
        // If quiet mode is enabled for work profile user
        int FLAG_WORK_PROFILE_QUIET_MODE_ENABLED = 1 << 3;
        // If quiet mode is enabled for private profile user
        int FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED = 1 << 4;

        /**
         * Returns an IntSet of page ids to bind first, synchronously if possible
         * or an empty IntSet
         * @param orderedScreenIds All the page ids to be bound
         */
        @NonNull
        default IntSet getPagesToBindSynchronously(IntArray orderedScreenIds) {
            return new IntSet();
        }

        default void clearPendingBinds() { }
        default void startBinding() { }

        default void bindItems(List<ItemInfo> shortcuts, boolean forceAnimateIcons) { }
        default void bindScreens(IntArray orderedScreenIds) { }
        default void setIsFirstPagePinnedItemEnabled(boolean isFirstPagePinnedItemEnabled) { }
        default void finishBindingItems(IntSet pagesBoundFirst) { }
        default void preAddApps() { }
        default void bindAppsAdded(IntArray newScreens,
                ArrayList<ItemInfo> addNotAnimated, ArrayList<ItemInfo> addAnimated) { }

        /**
         * Called when some persistent property of an item is modified
         */
        default void bindItemsModified(List<ItemInfo> items) { }

        /**
         * Binds updated incremental download progress
         */
        default void bindIncrementalDownloadProgressUpdated(AppInfo app) { }
        default void bindWorkspaceItemsChanged(List<WorkspaceItemInfo> updated) { }
        default void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets) { }
        default void bindRestoreItemsChange(HashSet<ItemInfo> updates) { }
        default void bindWorkspaceComponentsRemoved(Predicate<ItemInfo> matcher) { }
        default void bindAllWidgets(List<WidgetsListBaseEntry> widgets) { }
        default void bindSmartspaceWidget() { }

        /** Called when workspace has been bound. */
        default void onInitialBindComplete(IntSet boundPages, RunnableList pendingTasks,
                int workspaceItemCount, boolean isBindSync) {
            pendingTasks.executeAllAndDestroy();
        }

        default void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMap) { }

        /**
         * Binds extra item provided any external source
         */
        default void bindExtraContainerItems(FixedContainerItems item) { }

        default void bindAllApplications(AppInfo[] apps, int flags,
                Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
        }

        /**
         * Binds the cache of string resources
         */
        default void bindStringCache(StringCache cache) { }
    }
}
