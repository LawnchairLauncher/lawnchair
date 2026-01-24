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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_ARCHIVED;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.UserHandle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.BuildConfigs;
import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger.RestoreError;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.CollectionInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.IconRequestInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.UserIconInfo;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.net.URISyntaxException;
import java.security.InvalidParameterException;

import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import app.lawnchair.LawnchairApp;
import app.lawnchair.preferences2.PreferenceManager2;

/**
 * Extension of {@link Cursor} with utility methods for workspace loading.
 */
public class LoaderCursor extends CursorWrapper {

    private static final String TAG = "LoaderCursor";

    private final LongSparseArray<UserHandle> allUsers;

    private final Context mContext;
    private final LauncherModel mModel;
    private final PackageManagerHelper mPmHelper;
    private final IconCache mIconCache;
    private final InvariantDeviceProfile mIDP;
    private final @Nullable LauncherRestoreEventLogger mRestoreEventLogger;

    private final IntArray mItemsToRemove = new IntArray();
    private final IntArray mRestoredRows = new IntArray();
    private final IntSparseArrayMap<GridOccupancy> mOccupied = new IntSparseArrayMap<>();

    // CollectionInfo objects, which have not yet been loaded from the DB, but are expected to
    // found eventually as the loading progresses
    private final IntSparseArrayMap<CollectionInfo> mPendingCollectionInfo =
            new IntSparseArrayMap<>();

    private final int mIconIndex;
    public final int mTitleIndex;

    private final int mIdIndex;
    private final int mContainerIndex;
    private final int mItemTypeIndex;
    private final int mScreenIndex;
    private final int mCellXIndex;
    private final int mCellYIndex;
    private final int mProfileIdIndex;
    private final int mRestoredIndex;
    private final int mIntentIndex;

    private final int mAppWidgetIdIndex;
    private final int mAppWidgetProviderIndex;
    private final int mSpanXIndex;
    private final int mSpanYIndex;
    private final int mRankIndex;
    private final int mOptionsIndex;
    private final int mAppWidgetSourceIndex;

    private final PackageManager mPM;

    @Nullable
    private LauncherActivityInfo mActivityInfo;

    // Properties loaded per iteration
    public long serialNumber;
    public UserHandle user;
    public int id;
    public int container;
    public int itemType;
    public int restoreFlag;
    private PreferenceManager2 preferenceManager2;

    @AssistedInject
    public LoaderCursor(
            @Assisted Cursor cursor,
            @Assisted UserManagerState userManagerState,
            @Assisted @Nullable LauncherRestoreEventLogger restoreEventLogger,
            @ApplicationContext Context context,
            IconCache iconCache,
            InvariantDeviceProfile idp,
            LauncherModel model,
            PackageManagerHelper pmHelper) {
        super(cursor);
        mContext = context;
        mIconCache = iconCache;
        mIDP = idp;
        mModel = model;
        mPmHelper = pmHelper;

        allUsers = userManagerState.allUsers;
        mRestoreEventLogger = restoreEventLogger;

        preferenceManager2 = PreferenceManager2.getInstance(mContext);
        mPM = mContext.getPackageManager();

        // Init column indices
        mIconIndex = getColumnIndexOrThrow(Favorites.ICON);
        mTitleIndex = getColumnIndexOrThrow(Favorites.TITLE);

        mIdIndex = getColumnIndexOrThrow(Favorites._ID);
        mContainerIndex = getColumnIndexOrThrow(Favorites.CONTAINER);
        mItemTypeIndex = getColumnIndexOrThrow(Favorites.ITEM_TYPE);
        mScreenIndex = getColumnIndexOrThrow(Favorites.SCREEN);
        mCellXIndex = getColumnIndexOrThrow(Favorites.CELLX);
        mCellYIndex = getColumnIndexOrThrow(Favorites.CELLY);
        mProfileIdIndex = getColumnIndexOrThrow(Favorites.PROFILE_ID);
        mRestoredIndex = getColumnIndexOrThrow(Favorites.RESTORED);
        mIntentIndex = getColumnIndexOrThrow(Favorites.INTENT);

        mAppWidgetIdIndex = getColumnIndexOrThrow(Favorites.APPWIDGET_ID);
        mAppWidgetProviderIndex = getColumnIndexOrThrow(Favorites.APPWIDGET_PROVIDER);
        mSpanXIndex = getColumnIndexOrThrow(Favorites.SPANX);
        mSpanYIndex = getColumnIndexOrThrow(Favorites.SPANY);
        mRankIndex = getColumnIndexOrThrow(Favorites.RANK);
        mOptionsIndex = getColumnIndexOrThrow(Favorites.OPTIONS);
        mAppWidgetSourceIndex = getColumnIndexOrThrow(Favorites.APPWIDGET_SOURCE);
    }

    @Override
    public boolean moveToNext() {
        boolean result = super.moveToNext();
        if (result) {
            mActivityInfo = null;

            // Load common properties.
            itemType = getInt(mItemTypeIndex);
            container = getInt(mContainerIndex);
            id = getInt(mIdIndex);
            serialNumber = getInt(mProfileIdIndex);
            user = allUsers.get(serialNumber);
            restoreFlag = getInt(mRestoredIndex);
        }
        return result;
    }

    public Intent parseIntent() {
        String intentDescription = getString(mIntentIndex);
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
        if (!loadIconFromDb(info)) {
            info.bitmap = mIconCache.getDefaultIcon(info.user);
        }

        // TODO: If there's an explicit component and we can't install that, delete it.

        return info;
    }

    /**
     * Loads the icon from the cursor and updates the {@param info} if the icon is an app resource.
     */
    protected boolean loadIconFromDb(WorkspaceItemInfo info) {
        return createIconRequestInfo(info, false).loadIconFromDbBlob(mContext);
    }

    public IconRequestInfo<WorkspaceItemInfo> createIconRequestInfo(
            WorkspaceItemInfo wai, boolean useLowResIcon) {
        byte[] iconBlob = itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT || restoreFlag != 0
                || (wai.isInactiveArchive() && Flags.restoreArchivedAppIconsFromDb())
                ? getIconBlob() : null;
        return new IconRequestInfo<>(wai, mActivityInfo, iconBlob,
                DESKTOP_ICON_FLAG.withUseLowRes(useLowResIcon));
    }

    /**
     * Returns the icon data for at the current position
     */
    public byte[] getIconBlob() {
        return getBlob(mIconIndex);
    }

    /**
     * Returns the title or empty string
     */
    public String getTitle() {
        return Utilities.trim(getString(mTitleIndex));
    }

    /**
     * When loading an app widget for the workspace, returns it's app widget id
     */
    public int getAppWidgetId() {
        return getInt(mAppWidgetIdIndex);
    }

    /**
     * When loading an app widget for the workspace, returns the widget provider
     */
    public String getAppWidgetProvider() {
        return getString(mAppWidgetProviderIndex);
    }

    /**
     * Returns the x position for the item in the cell layout's grid
     */
    public int getSpanX() {
        return getInt(mSpanXIndex);
    }

    /**
     * Returns the y position for the item in the cell layout's grid
     */
    public int getSpanY() {
        return getInt(mSpanYIndex);
    }

    /**
     * Returns the rank for the item
     */
    public int getRank() {
        return getInt(mRankIndex);
    }

    /**
     * Returns the options for the item
     */
    public int getOptions() {
        return getInt(mOptionsIndex);
    }

    /**
     * When loading an app widget for the workspace, returns it's app widget source
     */
    public int getAppWidgetSource() {
        return getInt(mAppWidgetSourceIndex);
    }

    /**
     * Returns the screen that the item is on
     */
    public int getScreen() {
        return getInt(mScreenIndex);
    }

    /**
     * Returns the UX container that the item is in
     */
    public int getContainer() {
        return getInt(mContainerIndex);
    }

    /**
     * Make an WorkspaceItemInfo object for a restored application or shortcut item that points
     * to a package that is not yet installed on the system.
     */
    public WorkspaceItemInfo getRestoredItemInfo(Intent intent, boolean isArchived) {
        final WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.user = user;
        info.intent = intent;

        // the fallback icon
        if (!loadIconFromDb(info)) {
            FileLog.d(TAG, "loadIconFromDb failed, getting from cache - intent=" + intent);
            mIconCache.getTitleAndIcon(info, DEFAULT_LOOKUP_FLAG);
        }

        if (hasRestoreFlag(WorkspaceItemInfo.FLAG_RESTORED_ICON) || isArchived) {
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
        if (isArchived) info.runtimeStatusFlags |= FLAG_ARCHIVED;
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
        info.user = user;
        info.intent = newIntent;
        UserCache userCache = UserCache.getInstance(mContext);
        UserIconInfo userIconInfo = userCache.getUserInfo(user);
        if (mActivityInfo != null) {
            AppInfo.updateRuntimeFlagsForActivityTarget(info, mActivityInfo, userIconInfo,
                    ApiWrapper.INSTANCE.get(mContext), mPmHelper);
        }
        loadWorkspaceTitleAndIcon(useLowResIcon, loadIcon, info);
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

    @VisibleForTesting
    void loadWorkspaceTitleAndIcon(
            boolean useLowResIcon,
            boolean loadIconFromCache,
            WorkspaceItemInfo info
    ) {
        boolean isPreArchived = Flags.enableSupportForArchiving()
                && Flags.restoreArchivedAppIconsFromDb()
                && info.isInactiveArchive()
                && LauncherPrefs.get(mContext).get(LauncherPrefs.IS_FIRST_LOAD_AFTER_RESTORE);
        boolean preArchivedIconNotFound = isPreArchived && !loadIconFromDb(info);
        if (preArchivedIconNotFound) {
            Log.d(TAG, "loadIconFromDb failed for pre-archived icon, loading from cache."
                    + " Component=" + info.getTargetComponent());
            mIconCache.getTitleAndIcon(info, mActivityInfo,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(useLowResIcon));
        } else if (loadIconFromCache && !info.isInactiveArchive()) {
            mIconCache.getTitleAndIcon(info, mActivityInfo,
                    DEFAULT_LOOKUP_FLAG.withUseLowRes(useLowResIcon));
            if (mIconCache.isDefaultIcon(info.bitmap, user)) {
                Log.d(TAG, "Default Icon found in cache, trying DB instead. "
                        + " Component=" + info.getTargetComponent());
                loadIconFromDb(info);
            }
        }
    }

    /**
     * Returns a {@link ContentWriter} which can be used to update the current item.
     */
    public ContentWriter updater() {
       return new ContentWriter(mContext, new ContentWriter.CommitParams(
               mModel.getModelDbController(),
               BaseColumns._ID + "= ?", new String[]{Integer.toString(id)}));
    }

    /**
     * Marks the current item for removal
     */
    public void markDeleted(String reason, @RestoreError String errorType) {
        FileLog.e(TAG, reason);
        mItemsToRemove.add(id);
        if (mRestoreEventLogger != null) {
            mRestoreEventLogger.logSingleFavoritesItemRestoreFailed(itemType, errorType);
        }
    }

    /**
     * Removes any items marked for removal.
     * @return true is any item was removed.
     */
    public boolean commitDeleted() {
        if (mItemsToRemove.size() > 0) {
            // Remove dead items
            mModel.getModelDbController().delete(
                    Utilities.createDbSelectionQuery(Favorites._ID, mItemsToRemove), null);
            return true;
        }
        return false;
    }

    /**
     * Marks the current item as restored
     */
    public void markRestored() {
        if (restoreFlag != 0) {
            mRestoredRows.add(id);
            restoreFlag = 0;
        }
    }

    public boolean hasRestoreFlag(int flagMask) {
        return (restoreFlag & flagMask) != 0;
    }

    public void commitRestoredItems() {
        if (mRestoredRows.size() > 0) {
            // Update restored items that no longer require special handling
            ContentValues values = new ContentValues();
            values.put(Favorites.RESTORED, 0);
            mModel.getModelDbController().update(values,
                    Utilities.createDbSelectionQuery(Favorites._ID, mRestoredRows), null);
        }
        if (mRestoreEventLogger != null) {
            mRestoreEventLogger.reportLauncherRestoreResults();
        }
    }

    /**
     * Returns true is the item is on workspace or hotseat
     */
    public boolean isOnWorkspaceOrHotseat() {
        return container == Favorites.CONTAINER_DESKTOP || container == Favorites.CONTAINER_HOTSEAT;
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
        info.screenId = getInt(mScreenIndex);
        info.cellX = getInt(mCellXIndex);
        info.cellY = getInt(mCellYIndex);
    }

    /**
     * Return an existing FolderInfo object if we have encountered this ID previously,
     * or make a new one.
     */
    public CollectionInfo findOrMakeFolder(int id, IntSparseArrayMap<ItemInfo> loadedItems) {
        // See if a placeholder was created for us already
        ItemInfo info = loadedItems.get(id);
        if (info instanceof CollectionInfo c) return c;

        CollectionInfo pending = mPendingCollectionInfo.get(id);
        if (pending != null) return pending;

        // No placeholder -- create a new blank folder instance. At this point, we don't know
        // if the desired container is supposed to be a folder or an app pair. In the case that
        // it is an app pair, the blank folder will be replaced by a blank app pair when the app
        // pair is getting processed, in WorkspaceItemProcessor.processFolderOrAppPair().
        pending = new FolderInfo();
        pending.id = id;
        mPendingCollectionInfo.put(id, pending);
        return pending;
    }

    /**
     * Adds the {@param info} to {@param dataModel} if it does not overlap with any other item,
     * otherwise marks it for deletion.
     */
    public void checkAndAddItem(
            ItemInfo info, IntSparseArrayMap<ItemInfo> loadedItems, LoaderMemoryLogger logger) {
        if (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            // Ensure that it is a valid intent. An exception here will
            // cause the item loading to get skipped
            ShortcutKey.fromItemInfo(info);
        }
        if (checkItemPlacement(info)) {
            if (logger != null) {
                logger.addLog(
                        Log.DEBUG,
                        TAG,
                        String.format("Adding item to ID map: %s", info),
                        /* stackTrace= */ null);
            }
            loadedItems.put(info.id, info);
            if ((info.itemType == ITEM_TYPE_APP_PAIR
                    || info.itemType == ITEM_TYPE_DEEP_SHORTCUT
                    || info.itemType == ITEM_TYPE_APPLICATION)
                    && info.container != CONTAINER_DESKTOP
                    && info.container != CONTAINER_HOTSEAT) {
                findOrMakeFolder(info.container, loadedItems).add(info);
            }
            if (mRestoreEventLogger != null) {
                mRestoreEventLogger.logSingleFavoritesItemRestored(itemType);
            }
        } else {
            markDeleted("Item position overlap", RestoreError.OVERLAPPING_ITEM);
        }
    }

    /**
     * check & update map of what's occupied; used to discard overlapping/invalid items
     */
    protected boolean checkItemPlacement(ItemInfo item) {
        int containerIndex = item.screenId;
        if (item.container == Favorites.CONTAINER_HOTSEAT) {
            final GridOccupancy hotseatOccupancy =
                    mOccupied.get(Favorites.CONTAINER_HOTSEAT);

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
                mOccupied.put(Favorites.CONTAINER_HOTSEAT, occupancy);
                return true;
            }
        } else if (item.container != Favorites.CONTAINER_DESKTOP) {
            // Skip further checking if it is not the hotseat or workspace container
            return true;
        }

        final int countX = mIDP.numColumns;
        final int countY = mIDP.numRows;
        if (item.container == Favorites.CONTAINER_DESKTOP && item.cellX < 0 || item.cellY < 0
                || item.cellX + item.spanX > countX || item.cellY + item.spanY > countY) {
            Log.e(TAG, "Error loading shortcut " + item
                    + " into cell (" + containerIndex + "-" + item.screenId + ":"
                    + item.cellX + "," + item.cellY
                    + ") out of screen bounds ( " + countX + "x" + countY + ")");
            return false;
        }

        if (!mOccupied.containsKey(item.screenId)) {
            GridOccupancy screen = new GridOccupancy(countX + 1, countY + 1);
            if (item.screenId == Workspace.FIRST_SCREEN_ID && PreferenceExtensionsKt.firstBlocking(preferenceManager2.getEnableSmartspace())) {
                // Mark the first X columns (X is width of the search container) in the first row as
                // occupied (if the feature is enabled) in order to account for the search
                // container.
                int spanX = mIDP.numSearchContainerColumns;
                int spanY = 1;
                screen.markCells(0, 0, spanX, spanY, true);
            }
            mOccupied.put(item.screenId, screen);
        }
        final GridOccupancy occupancy = mOccupied.get(item.screenId);

        // Check if any workspace icons overlap with each other
        if (occupancy.isRegionVacant(item.cellX, item.cellY, item.spanX, item.spanY)) {
            occupancy.markCells(item, true);
            return true;
        } else {
            Log.e(TAG, "Error loading shortcut " + item
                    + " into cell (" + containerIndex + "-" + item.screenId + ":"
                    + item.cellX + "," + item.cellX + "," + item.spanX + "," + item.spanY
                    + ") already occupied");
            return PreferenceExtensionsKt.firstBlocking(preferenceManager2.getAllowWidgetOverlap());
        }
    }

    @AssistedFactory
    public interface LoaderCursorFactory {

        LoaderCursor createLoaderCursor(Cursor cursor,
                UserManagerState userManagerState,
                @Nullable LauncherRestoreEventLogger restoreEventLogger);
    }
}
