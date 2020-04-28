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

import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.MutableInt;

import com.android.launcher3.AppInfo;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.PromiseAppInfo;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.DumpTargetWrapper;
import com.android.launcher3.model.nano.LauncherDumpProto;
import com.android.launcher3.model.nano.LauncherDumpProto.ContainerType;
import com.android.launcher3.model.nano.LauncherDumpProto.DumpTarget;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.widget.WidgetListRowEntry;

import com.google.protobuf.nano.MessageNano;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
     * True if the launcher has permission to access deep shortcuts.
     */
    public boolean hasShortcutHostPermission;

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
        if (Arrays.asList(args).contains("--proto")) {
            dumpProto(prefix, fd, writer, args);
            return;
        }
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

    private synchronized void dumpProto(String prefix, FileDescriptor fd, PrintWriter writer,
            String[] args) {

        // Add top parent nodes. (L1)
        DumpTargetWrapper hotseat = new DumpTargetWrapper(ContainerType.HOTSEAT, 0);
        IntSparseArrayMap<DumpTargetWrapper> workspaces = new IntSparseArrayMap<>();
        IntArray workspaceScreens = collectWorkspaceScreens();
        for (int i = 0; i < workspaceScreens.size(); i++) {
            workspaces.put(workspaceScreens.get(i),
                    new DumpTargetWrapper(ContainerType.WORKSPACE, i));
        }
        DumpTargetWrapper dtw;
        // Add non leaf / non top nodes (L2)
        for (int i = 0; i < folders.size(); i++) {
            FolderInfo fInfo = folders.valueAt(i);
            dtw = new DumpTargetWrapper(ContainerType.FOLDER, folders.size());
            dtw.writeToDumpTarget(fInfo);
            for(WorkspaceItemInfo sInfo: fInfo.contents) {
                DumpTargetWrapper child = new DumpTargetWrapper(sInfo);
                child.writeToDumpTarget(sInfo);
                dtw.add(child);
            }
            if (fInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                hotseat.add(dtw);
            } else if (fInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                workspaces.get(fInfo.screenId).add(dtw);
            }
        }
        // Add leaf nodes (L3): *Info
        for (int i = 0; i < workspaceItems.size(); i++) {
            ItemInfo info = workspaceItems.get(i);
            if (info instanceof FolderInfo) {
                continue;
            }
            dtw = new DumpTargetWrapper(info);
            dtw.writeToDumpTarget(info);
            if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                hotseat.add(dtw);
            } else if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                workspaces.get(info.screenId).add(dtw);
            }
        }
        for (int i = 0; i < appWidgets.size(); i++) {
            ItemInfo info = appWidgets.get(i);
            dtw = new DumpTargetWrapper(info);
            dtw.writeToDumpTarget(info);
            if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                hotseat.add(dtw);
            } else if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                workspaces.get(info.screenId).add(dtw);
            }
        }


        // Traverse target wrapper
        ArrayList<DumpTarget> targetList = new ArrayList<>();
        targetList.addAll(hotseat.getFlattenedList());
        for (int i = 0; i < workspaces.size(); i++) {
            targetList.addAll(workspaces.valueAt(i).getFlattenedList());
        }

        if (Arrays.asList(args).contains("--debug")) {
            for (int i = 0; i < targetList.size(); i++) {
                writer.println(prefix + DumpTargetWrapper.getDumpTargetStr(targetList.get(i)));
            }
            return;
        } else {
            LauncherDumpProto.LauncherImpression proto = new LauncherDumpProto.LauncherImpression();
            proto.targets = new DumpTarget[targetList.size()];
            for (int i = 0; i < targetList.size(); i++) {
                proto.targets[i] = targetList.get(i);
            }
            FileOutputStream fos = new FileOutputStream(fd);
            try {

                fos.write(MessageNano.toByteArray(proto));
                Log.d(TAG, MessageNano.toByteArray(proto).length + "Bytes");
            } catch (IOException e) {
                Log.e(TAG, "Exception writing dumpsys --proto", e);
            }
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
                    if (FeatureFlags.IS_DOGFOOD_BUILD) {
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
                        DeepShortcutManager.getInstance(context).unpinShortcut(pinnedShortcut);
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
                    DeepShortcutManager.getInstance(context).pinShortcut(pinnedShortcut);
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
                    && (shortcut.isDeclaredInManifest() || shortcut.isDynamic());
            if (shouldShowInContainer) {
                ComponentKey targetComponent
                        = new ComponentKey(shortcut.getActivity(), shortcut.getUserHandle());

                Integer previousCount = deepShortcutMap.get(targetComponent);
                deepShortcutMap.put(targetComponent, previousCount == null ? 1 : previousCount + 1);
            }
        }
    }

    public interface Callbacks {
        void rebindModel();

        int getCurrentWorkspaceScreen();
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

        void bindAllApplications(AppInfo[] apps);
    }
}
