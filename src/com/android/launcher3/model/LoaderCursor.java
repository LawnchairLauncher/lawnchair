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
import android.content.Intent.ShortcutIconResource;
import android.content.pm.LauncherActivityInfo;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.UserHandle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import com.android.launcher3.IconCache;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.LongArrayMap;
import com.android.launcher3.util.PackageManagerHelper;

import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.util.ArrayList;

/**
 * Extension of {@link Cursor} with utility methods for workspace loading.
 */
public class LoaderCursor extends CursorWrapper {

    private static final String TAG = "LoaderCursor";

    public final LongSparseArray<UserHandle> allUsers = new LongSparseArray<>();

    private final Context mContext;
    private final UserManagerCompat mUserManager;
    private final IconCache mIconCache;
    private final InvariantDeviceProfile mIDP;

    private final ArrayList<Long> itemsToRemove = new ArrayList<>();
    private final ArrayList<Long> restoredRows = new ArrayList<>();
    private final LongArrayMap<GridOccupancy> occupied = new LongArrayMap<>();

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

    // Properties loaded per iteration
    public long serialNumber;
    public UserHandle user;
    public long id;
    public long container;
    public int itemType;
    public int restoreFlag;

    public LoaderCursor(Cursor c, LauncherAppState app) {
        super(c);
        mContext = app.getContext();
        mIconCache = app.getIconCache();
        mIDP = app.getInvariantDeviceProfile();
        mUserManager = UserManagerCompat.getInstance(mContext);

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
            // Load common properties.
            itemType = getInt(itemTypeIndex);
            container = getInt(containerIndex);
            id = getLong(idIndex);
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

    public ShortcutInfo loadSimpleShortcut() {
        final ShortcutInfo info = new ShortcutInfo();
        // Non-app shortcuts are only supported for current user.
        info.user = user;
        info.itemType = itemType;
        info.title = getTitle();
        info.iconBitmap = loadIcon(info);
        // the fallback icon
        if (info.iconBitmap == null) {
            info.iconBitmap = mIconCache.getDefaultIcon(info.user);
        }

        // TODO: If there's an explicit component and we can't install that, delete it.

        return info;
    }

    /**
     * Loads the icon from the cursor and updates the {@param info} if the icon is an app resource.
     */
    protected Bitmap loadIcon(ShortcutInfo info) {
        Bitmap icon = null;
        if (itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
            String packageName = getString(iconPackageIndex);
            String resourceName = getString(iconResourceIndex);
            if (!TextUtils.isEmpty(packageName) || !TextUtils.isEmpty(resourceName)) {
                info.iconResource = new ShortcutIconResource();
                info.iconResource.packageName = packageName;
                info.iconResource.resourceName = resourceName;
                icon = LauncherIcons.createIconBitmap(info.iconResource, mContext);
            }
        }
        if (icon == null) {
            // Failed to load from resource, try loading from DB.
            byte[] data = getBlob(iconIndex);
            try {
                icon = LauncherIcons.createIconBitmap(
                        BitmapFactory.decodeByteArray(data, 0, data.length), mContext);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load icon for info " + info, e);
                return null;
            }
        }
        if (icon == null) {
            Log.e(TAG, "Failed to load icon for info " + info);
        }
        return icon;
    }

    /**
     * Returns the title or empty string
     */
    private String getTitle() {
        String title = getString(titleIndex);
        return TextUtils.isEmpty(title) ? "" : Utilities.trim(title);
    }


    /**
     * Make an ShortcutInfo object for a restored application or shortcut item that points
     * to a package that is not yet installed on the system.
     */
    public ShortcutInfo getRestoredItemInfo(Intent intent) {
        final ShortcutInfo info = new ShortcutInfo();
        info.user = user;
        info.intent = intent;

        info.iconBitmap = loadIcon(info);
        // the fallback icon
        if (info.iconBitmap == null) {
            mIconCache.getTitleAndIcon(info, false /* useLowResIcon */);
        }

        if (hasRestoreFlag(ShortcutInfo.FLAG_RESTORED_ICON)) {
            String title = getTitle();
            if (!TextUtils.isEmpty(title)) {
                info.title = Utilities.trim(title);
            }
        } else if  (hasRestoreFlag(ShortcutInfo.FLAG_AUTOINSTALL_ICON)) {
            if (TextUtils.isEmpty(info.title)) {
                info.title = getTitle();
            }
        } else {
            throw new InvalidParameterException("Invalid restoreType " + restoreFlag);
        }

        info.contentDescription = mUserManager.getBadgedLabelForUser(info.title, info.user);
        info.itemType = itemType;
        info.status = restoreFlag;
        return info;
    }

    /**
     * Make an ShortcutInfo object for a shortcut that is an application.
     */
    public ShortcutInfo getAppShortcutInfo(
            Intent intent, boolean allowMissingTarget, boolean useLowResIcon) {
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
        LauncherActivityInfo lai = LauncherAppsCompat.getInstance(mContext)
                .resolveActivity(newIntent, user);
        if ((lai == null) && !allowMissingTarget) {
            Log.d(TAG, "Missing activity found in getShortcutInfo: " + componentName);
            return null;
        }

        final ShortcutInfo info = new ShortcutInfo();
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        info.user = user;
        info.intent = newIntent;

        mIconCache.getTitleAndIcon(info, lai, useLowResIcon);
        if (mIconCache.isDefaultIcon(info.iconBitmap, user)) {
            Bitmap icon = loadIcon(info);
            info.iconBitmap = icon != null ? icon : info.iconBitmap;
        }

        if (lai != null && PackageManagerHelper.isAppSuspended(lai.getApplicationInfo())) {
            info.isDisabled = ShortcutInfo.FLAG_DISABLED_SUSPENDED;
        }

        // from the db
        if (TextUtils.isEmpty(info.title)) {
            info.title = getTitle();
        }

        // fall back to the class name of the activity
        if (info.title == null) {
            info.title = componentName.getClassName();
        }

        info.contentDescription = mUserManager.getBadgedLabelForUser(info.title, info.user);
        return info;
    }

    /**
     * Returns a {@link ContentWriter} which can be used to update the current item.
     */
    public ContentWriter updater() {
       return new ContentWriter(mContext, new ContentWriter.CommitParams(
               BaseColumns._ID + "= ?", new String[]{Long.toString(id)}));
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
            mContext.getContentResolver().delete(LauncherSettings.Favorites.CONTENT_URI,
                    Utilities.createDbSelectionQuery(
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
            mContext.getContentResolver().update(LauncherSettings.Favorites.CONTENT_URI, values,
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

    /**
     * Adds the {@param info} to {@param dataModel} if it does not overlap with any other item,
     * otherwise marks it for deletion.
     */
    public void checkAndAddItem(ItemInfo info, BgDataModel dataModel) {
        if (checkItemPlacement(info, dataModel.workspaceScreens)) {
            dataModel.addItem(mContext, info, false);
        } else {
            markDeleted("Item position overlap");
        }
    }

    /**
     * check & update map of what's occupied; used to discard overlapping/invalid items
     */
    protected boolean checkItemPlacement(ItemInfo item, ArrayList<Long> workspaceScreens) {
        long containerIndex = item.screenId;
        if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            // Return early if we detect that an item is under the hotseat button
            if (!FeatureFlags.NO_ALL_APPS_ICON &&
                    mIDP.isAllAppsButtonRank((int) item.screenId)) {
                Log.e(TAG, "Error loading shortcut into hotseat " + item
                        + " into position (" + item.screenId + ":" + item.cellX + ","
                        + item.cellY + ") occupied by all apps");
                return false;
            }

            final GridOccupancy hotseatOccupancy =
                    occupied.get((long) LauncherSettings.Favorites.CONTAINER_HOTSEAT);

            if (item.screenId >= mIDP.numHotseatIcons) {
                Log.e(TAG, "Error loading shortcut " + item
                        + " into hotseat position " + item.screenId
                        + ", position out of bounds: (0 to " + (mIDP.numHotseatIcons - 1)
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
                    hotseatOccupancy.cells[(int) item.screenId][0] = true;
                    return true;
                }
            } else {
                final GridOccupancy occupancy = new GridOccupancy(mIDP.numHotseatIcons, 1);
                occupancy.cells[(int) item.screenId][0] = true;
                occupied.put((long) LauncherSettings.Favorites.CONTAINER_HOTSEAT, occupancy);
                return true;
            }
        } else if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            if (!workspaceScreens.contains((Long) item.screenId)) {
                // The item has an invalid screen id.
                return false;
            }
        } else {
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
            if (item.screenId == Workspace.FIRST_SCREEN_ID) {
                // Mark the first row as occupied (if the feature is enabled)
                // in order to account for the QSB.
                screen.markCells(0, 0, countX + 1, 1, FeatureFlags.QSB_ON_FIRST_SCREEN);
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
