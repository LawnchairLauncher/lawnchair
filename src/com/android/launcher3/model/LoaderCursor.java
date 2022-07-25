/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.IconRequestInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSparseArrayMap;

import java.net.URISyntaxException;
import java.security.InvalidParameterException;

/**
 * Extension of {@link Cursor} with utility methods for workspace loading.
 */
public class LoaderCursor extends CursorWrapper {

    private static final String TAG = "LoaderCursor";

    private final LongSparseArray<UserHandle> allUsers;

    private final Uri mContentUri;
    private final Context mContext;
    private final PackageManager mPM;
    private final IconCache mIconCache;
    private final InvariantDeviceProfile mIDP;

    private final IntArray itemsToRemove = new IntArray();
    private final IntArray restoredRows = new IntArray();
    private final IntSparseArrayMap<GridOccupancy> occupied = new IntSparseArrayMap<>();

    private final int iconPackageIndex;
    private final int iconResourceIndex;
    private final int iconIndex;
    public final int titleIndex;

    private final int idIndex;
    private final int containerIndex;
    private final int itemTypeIndex;
    private final int screenIndex;
    private final int cellXIndex;
    private final int cellYIndex;
    private final int profileIdIndex;
    private final int restoredIndex;
    private final int intentIndex;

    @Nullable
    private LauncherActivityInfo mActivityInfo;

    // Properties loaded per iteration
    public long serialNumber;
    public UserHandle user;
    public int id;
    public int container;
    public int itemType;
    public int restoreFlag;

    public LoaderCursor(Cursor cursor, Uri contentUri, LauncherAppState app,
            UserManagerState userManagerState) {
        super(cursor);

        allUsers = userManagerState.allUsers;
        mContentUri = contentUri;
        mContext = app.getContext();
        mIconCache = app.getIconCache();
        mIDP = app.getInvariantDeviceProfile();
        mPM = mContext.getPackageManager();

        // Init column indices
        iconIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.ICON);
        iconPackageIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_PACKAGE);
        iconResourceIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_RESOURCE);
        titleIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);

        idIndex = getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
        containerIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
        itemTypeIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
        screenIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
        cellXIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
        cellYIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
        profileIdIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.PROFILE_ID);
        restoredIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.RESTORED);
        intentIndex = getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
    }

    @Override
    public boolean moveToNext() {
        boolean result = super.moveToNext();
        if (result) {
            mActivityInfo = null;

            // Load common properties.
            itemType = getInt(itemTypeIndex);
            container = getInt(containerIndex);
            id = getInt(idIndex);
            serialNumber = getInt(profileIdIndex);
            user = allUsers.get(serialNumber);
            restoreFlag = getInt(restoredIndex);
        }
        return result;
    }

    public Intent parseIntent() {
        String intentDescription = getString(intentIndex);
        try {
            return TextUtils.isEmpty(intentDescription) ?
                    null : Intent.parseUri(intentDescription, 0);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Error parsing Intent");
            return null;
        }
    }

    @VisibleForTesting
    public WorkspaceItemInfo loadSimpleWorkspaceItem() {
        final WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.intent = new Intent();
        // Non-app shortcuts are only supported for current user.
        info.user = user;
        info.itemType = itemType;
        info.title = getTitle();
        // the fallback icon
        if (!loadIcon(info)) {
            info.bitmap = mIconCache.getDefaultIcon(info.user);
        }

        // TODO: If there's an explicit component and we can't install that, delete it.

        return info;
    }

    /**
     * Loads the icon from the cursor and updates the {@param info} if the icon is an app resource.
     */
    protected boolean loadIcon(WorkspaceItemInfo info) {
        return createIconRequestInfo(info, false).loadWorkspaceIcon(mContext);
    }

    public IconRequestInfo<WorkspaceItemInfo> createIconRequestInfo(
            WorkspaceItemInfo wai, boolean useLowResIcon) {
        String packageName = itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
                ? getString(iconPackageIndex) : null;
        String resourceName = itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
                ? getString(iconResourceIndex) : null;
        byte[] iconBlob = itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
                || itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT
                || restoreFlag != 0
                ? getBlob(iconIndex) : null;

        return new IconRequestInfo<>(
                wai, mActivityInfo, packageName, resourceName, iconBlob, useLowResIcon);
    }

    /**
     * Returns the title or empty string
     */
    private String getTitle() {
        return Utilities.trim(getString(titleIndex));
    }

    /**
     * Make an WorkspaceItemInfo object for a restored application or shortcut item that points
     * to a package that is not yet installed on the system.
     */
    public WorkspaceItemInfo getRestoredItemInfo(Intent intent) {
        final WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.user = user;
        info.intent = intent;

        // the fallback icon
        if (!loadIcon(info)) {
            mIconCache.getTitleAndIcon(info, false /* useLowResIcon */);
        }

        if (hasRestoreFlag(WorkspaceItemInfo.FLAG_RESTORED_ICON)) {
            String title = getTitle();
            if (!TextUtils.isEmpty(title)) {
                info.title = Utilities.trim(title);
            }
        } else if (hasRestoreFlag(WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON)) {
            if (TextUtils.isEmpty(info.title)) {
                info.title = getTitle();
            }
        } else {
            throw new InvalidParameterException("Invalid restoreType " + restoreFlag);
        }

        info.contentDescription = mPM.getUserBadgedLabel(info.title, info.user);
        info.itemType = itemType;
        info.status = restoreFlag;
        return info;
    }

    public LauncherActivityInfo getLauncherActivityInfo() {
        return mActivityInfo;
    }

    /**
     * Make an WorkspaceItemInfo object for a shortcut that is an application.
     */
    public WorkspaceItemInfo getAppShortcutInfo(
            Intent intent, boolean allowMissingTarget, boolean useLowResIcon) {
        return getAppShortcutInfo(intent, allowMissingTarget, useLowResIcon, true);
    }

    public WorkspaceItemInfo getAppShortcutInfo(
            Intent intent, boolean allowMissingTarget, boolean useLowResIcon, boolean loadIcon) {
        if (user == null) {
            Log.d(TAG, "Null user found in getShortcutInfo");
            return null;
        }

        ComponentName componentName = intent.getComponent();
        if (componentName == null) {
            Log.d(TAG, "Missing component found in getShortcutInfo");
            return null;
        }

        Intent newIntent = new Intent(Intent.ACTION_MAIN, null);
        newIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        newIntent.setComponent(componentName);
        mActivityInfo = mContext.getSystemService(LauncherApps.class)
                .resolveActivity(newIntent, user);
        if ((mActivityInfo == null) && !allowMissingTarget) {
            Log.d(TAG, "Missing activity found in getShortcutInfo: " + componentName);
            return null;
        }

        final WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.itemType = Favorites.ITEM_TYPE_APPLICATION;
        info.user = user;
        info.intent = newIntent;

        if (loadIcon) {
            mIconCache.getTitleAndIcon(info, mActivityInfo, useLowResIcon);
            if (mIconCache.isDefaultIcon(info.bitmap, user)) {
                loadIcon(info);
            }
        }

        if (mActivityInfo != null) {
            AppInfo.updateRuntimeFlagsForActivityTarget(info, mActivityInfo);
        }

        // from the db
        if (TextUtils.isEmpty(info.title)) {
            if (loadIcon) {
                info.title = getTitle();

                // fall back to the class name of the activity
                if (info.title == null) {
                    info.title = componentName.getClassName();
                }
            } else {
                info.title = "";
            }
        }

        info.contentDescription = mPM.getUserBadgedLabel(info.title, info.user);
        return info;
    }

    /**
     * Returns a {@link ContentWriter} which can be used to update the current item.
     */
    public ContentWriter updater() {
       return new ContentWriter(mContext, new ContentWriter.CommitParams(
               BaseColumns._ID + "= ?", new String[]{Integer.toString(id)}));
    }

    /**
     * Marks the current item for removal
     */
    public void markDeleted(String reason) {
        FileLog.e(TAG, reason);
        itemsToRemove.add(id);
    }

    /**
     * Removes any items marked for removal.
     * @return true is any item was removed.
     */
    public boolean commitDeleted() {
        if (itemsToRemove.size() > 0) {
            // Remove dead items
            mContext.getContentResolver().delete(mContentUri, Utilities.createDbSelectionQuery(
                    LauncherSettings.Favorites._ID, itemsToRemove), null);
            return true;
        }
        return false;
    }

    /**
     * Marks the current item as restored
     */
    public void markRestored() {
        if (restoreFlag != 0) {
            restoredRows.add(id);
            restoreFlag = 0;
        }
    }

    public boolean hasRestoreFlag(int flagMask) {
        return (restoreFlag & flagMask) != 0;
    }

    public void commitRestoredItems() {
        if (restoredRows.size() > 0) {
            // Update restored items that no longer require special handling
            ContentValues values = new ContentValues();
            values.put(LauncherSettings.Favorites.RESTORED, 0);
            mContext.getContentResolver().update(mContentUri, values,
                    Utilities.createDbSelectionQuery(
                            LauncherSettings.Favorites._ID, restoredRows), null);
        }
    }

    /**
     * Returns true is the item is on workspace or hotseat
     */
    public boolean isOnWorkspaceOrHotseat() {
        return container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
    }

    /**
     * Applies the following properties:
     * {@link ItemInfo#id}
     * {@link ItemInfo#container}
     * {@link ItemInfo#screenId}
     * {@link ItemInfo#cellX}
     * {@link ItemInfo#cellY}
     */
    public void applyCommonProperties(ItemInfo info) {
        info.id = id;
        info.container = container;
        info.screenId = getInt(screenIndex);
        info.cellX = getInt(cellXIndex);
        info.cellY = getInt(cellYIndex);
    }

    public void checkAndAddItem(ItemInfo info, BgDataModel dataModel) {
        checkAndAddItem(info, dataModel, null);
    }

    /**
     * Adds the {@param info} to {@param dataModel} if it does not overlap with any other item,
     * otherwise marks it for deletion.
     */
    public void checkAndAddItem(
            ItemInfo info, BgDataModel dataModel, LoaderMemoryLogger logger) {
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            // Ensure that it is a valid intent. An exception here will
            // cause the item loading to get skipped
            ShortcutKey.fromItemInfo(info);
        }
        if (checkItemPlacement(info)) {
            dataModel.addItem(mContext, info, false, logger);
        } else {
            markDeleted("Item position overlap");
        }
    }

    /**
     * check & update map of what's occupied; used to discard overlapping/invalid items
     */
    protected boolean checkItemPlacement(ItemInfo item) {
        int containerIndex = item.screenId;
        if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            final GridOccupancy hotseatOccupancy =
                    occupied.get(LauncherSettings.Favorites.CONTAINER_HOTSEAT);

            if (item.screenId >= mIDP.numDatabaseHotseatIcons) {
                Log.e(TAG, "Error loading shortcut " + item
                        + " into hotseat position " + item.screenId
                        + ", position out of bounds: (0 to " + (mIDP.numDatabaseHotseatIcons - 1)
                        + ")");
                return false;
            }

            if (hotseatOccupancy != null) {
                if (hotseatOccupancy.cells[(int) item.screenId][0]) {
                    Log.e(TAG, "Error loading shortcut into hotseat " + item
                            + " into position (" + item.screenId + ":" + item.cellX + ","
                            + item.cellY + ") already occupied");
                    return false;
                } else {
                    hotseatOccupancy.cells[item.screenId][0] = true;
                    return true;
                }
            } else {
                final GridOccupancy occupancy = new GridOccupancy(mIDP.numDatabaseHotseatIcons, 1);
                occupancy.cells[item.screenId][0] = true;
                occupied.put(LauncherSettings.Favorites.CONTAINER_HOTSEAT, occupancy);
                return true;
            }
        } else if (item.container != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            // Skip further checking if it is not the hotseat or workspace container
            return true;
        }

        final int countX = mIDP.numColumns;
        final int countY = mIDP.numRows;
        if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                item.cellX < 0 || item.cellY < 0 ||
                item.cellX + item.spanX > countX || item.cellY + item.spanY > countY) {
            Log.e(TAG, "Error loading shortcut " + item
                    + " into cell (" + containerIndex + "-" + item.screenId + ":"
                    + item.cellX + "," + item.cellY
                    + ") out of screen bounds ( " + countX + "x" + countY + ")");
            return false;
        }

        if (!occupied.containsKey(item.screenId)) {
            GridOccupancy screen = new GridOccupancy(countX + 1, countY + 1);
            if (item.screenId == Workspace.FIRST_SCREEN_ID && FeatureFlags.QSB_ON_FIRST_SCREEN) {
                // Mark the first X columns (X is width of the search container) in the first row as
                // occupied (if the feature is enabled) in order to account for the search
                // container.
                int spanX = mIDP.numSearchContainerColumns;
                int spanY = FeatureFlags.EXPANDED_SMARTSPACE.get() ? 2 : 1;
                screen.markCells(0, 0, spanX, spanY, true);
            }
            occupied.put(item.screenId, screen);
        }
        final GridOccupancy occupancy = occupied.get(item.screenId);

        // Check if any workspace icons overlap with each other
        if (occupancy.isRegionVacant(item.cellX, item.cellY, item.spanX, item.spanY)) {
            occupancy.markCells(item, true);
            return true;
        } else {
            Log.e(TAG, "Error loading shortcut " + item
                    + " into cell (" + containerIndex + "-" + item.screenId + ":"
                    + item.cellX + "," + item.cellX + "," + item.spanX + "," + item.spanY
                    + ") already occupied");
            return false;
        }
    }
}
