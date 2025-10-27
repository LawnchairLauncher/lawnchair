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

import static com.android.launcher3.BuildConfigs.QSB_ON_FIRST_SCREEN;
import static com.android.launcher3.BuildConfigs.WIDGETS_ENABLED;
import static com.android.launcher3.Flags.enableSmartspaceRemovalToggle;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER;
import static com.android.launcher3.Utilities.SHOULD_SHOW_FIRST_PAGE_WIDGET;
import static com.android.launcher3.shortcuts.ShortcutRequest.PINNED;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.BuildConfigs;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ItemInflater;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import app.lawnchair.LawnchairApp;

/**
 * All the data stored in-memory and managed by the LauncherModel
 *
 * All the static data should be accessed on the background thread, A lock should be acquired on
 * this object when accessing any data from this model.
 */
@LauncherAppSingleton
public class BgDataModel {

    private static final String TAG = "BgDataModel";

    /**
     * Map of all the ItemInfos (shortcuts, folders, and widgets) created by
     * LauncherModel to their ids
     */
    public final IntSparseArrayMap<ItemInfo> itemsIdMap = new IntSparseArrayMap<>();

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
    public final WidgetsModel widgetsModel;

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
            && !enableSmartspaceRemovalToggle();

    @Inject
    public BgDataModel(WidgetsModel widgetsModel) {
        this.widgetsModel = widgetsModel;
    }

    /**
     * Clears all the data
     */
    public synchronized void clear() {
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
            if (item.container == CONTAINER_DESKTOP) {
                screenSet.add(item.screenId);
            }
        }
        if ((FeatureFlags.QSB_ON_FIRST_SCREEN
                && !SHOULD_SHOW_FIRST_PAGE_WIDGET)
                || screenSet.isEmpty()) {
            screenSet.add(Workspace.FIRST_SCREEN_ID);
        }
        return screenSet.getArray();
    }

    public synchronized void dump(String prefix, FileDescriptor fd, PrintWriter writer,
            String[] args) {
        writer.println(prefix + "Data Model:");
        writer.println(prefix + " ---- items id map ");
        for (int i = 0; i < itemsIdMap.size(); i++) {
            writer.println(prefix + '\t' + itemsIdMap.valueAt(i).toString());
        }
        writer.println(prefix + " ---- extra items ");
        for (int i = 0; i < extraItems.size(); i++) {
            writer.println(prefix + '\t' + extraItems.valueAt(i).toString());
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

    public synchronized void removeItem(Context context, List<? extends ItemInfo> items) {
        if (BuildConfigs.IS_STUDIO_BUILD) {
            items.stream()
                    .filter(item -> item.itemType == ITEM_TYPE_FOLDER
                            || item.itemType == ITEM_TYPE_APP_PAIR)
                    .forEach(item -> itemsIdMap.stream()
                            .filter(info -> info.container == item.id)
                            // We are deleting a collection which still contains items that
                            // think they are contained by that collection.
                            .forEach(info -> Log.e(TAG,
                                    "deleting a collection (" + item + ") which still contains"
                                            + " items (" + info + ")")));
        }

        items.forEach(item -> itemsIdMap.remove(item.id));
        items.stream().map(info -> info.user).distinct().forEach(
                user -> updateShortcutPinnedState(context, user));
    }

    public synchronized void addItem(Context context, ItemInfo item, boolean newItem) {
        itemsIdMap.put(item.id, item);
        if (newItem && item.itemType == ITEM_TYPE_DEEP_SHORTCUT) {
            updateShortcutPinnedState(context, item.user);
        }
        if (BuildConfigs.IS_DEBUG_DEVICE
                && newItem
                && item.container != CONTAINER_DESKTOP
                && item.container != CONTAINER_HOTSEAT
                && !(itemsIdMap.get(item.container) instanceof CollectionInfo)) {
            // Adding an item to a nonexistent collection.
            Log.e(TAG, "attempted to add item: " + item + " to a nonexistent app collection");
        }
    }

    /**
     * Updates the deep shortcuts state in system to match out internal model, pinning any missing
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
        if (!WIDGETS_ENABLED) {
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
                        .filter(wi -> wi.itemType == ITEM_TYPE_DEEP_SHORTCUT)
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
                    FileLog.d(TAG, "updateShortcutPinnedState:"
                            + " Pinning Shortcuts: " + entry.getKey() + ": " + modelShortcuts);
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
                FileLog.d(TAG, "updateShortcutPinnedState:"
                        + " Unpinning extra Shortcuts for package: " + packageName
                        + ": " + systemMap.get(packageName));
                context.getSystemService(LauncherApps.class).pinShortcuts(
                        packageName, Collections.emptyList(), user);
            } catch (SecurityException | IllegalStateException e) {
                Log.w(TAG, "Failed to unpin shortcut", e);
            }
        });
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

        @Nullable
        default ItemInflater getItemInflater() {
            return null;
        }

        default void bindItems(@NonNull List<ItemInfo> shortcuts, boolean forceAnimateIcons) { }
        /** Alternate method to bind preinflated views */
        default void bindInflatedItems(@NonNull List<Pair<ItemInfo, View>> items) { }

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

        /** Called when a runtime property of the ItemInfo is updated due to some system event */
        default void bindItemsUpdated(Set<ItemInfo> updates) { }
        default void bindWorkspaceComponentsRemoved(Predicate<ItemInfo> matcher) { }

        /**
         * Binds the app widgets to the providers that share widgets with the UI.
         */
        default void bindAllWidgets(@NonNull List<WidgetsListBaseEntry> widgets) { }

        default void bindSmartspaceWidget() { }

        /** Called when workspace has been bound. */
        default void onInitialBindComplete(@NonNull IntSet boundPages,
                @NonNull RunnableList pendingTasks,
                @NonNull RunnableList onCompleteSignal,
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
