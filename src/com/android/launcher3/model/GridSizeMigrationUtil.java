/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME;
import static com.android.launcher3.LauncherSettings.Favorites.TMP_TABLE;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SMARTSPACE_REMOVAL;
import static com.android.launcher3.config.FeatureFlags.shouldShowFirstPageWidget;
import static com.android.launcher3.model.LoaderTask.SMARTSPACE_ON_HOME_SCREEN;
import static com.android.launcher3.provider.LauncherDbUtils.copyTable;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetManagerHelper;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class takes care of shrinking the workspace (by maximum of one row and one column), as a
 * result of restoring from a larger device or device density change.
 */
public class GridSizeMigrationUtil {

    private static final String TAG = "GridSizeMigrationUtil";
    private static final boolean DEBUG = true;

    private GridSizeMigrationUtil() {
        // Util class should not be instantiated
    }

    /**
     * Check given a new IDP, if migration is necessary.
     */
    public static boolean needsToMigrate(Context context, InvariantDeviceProfile idp) {
        return needsToMigrate(new DeviceGridState(context), new DeviceGridState(idp));
    }

    private static boolean needsToMigrate(
            DeviceGridState srcDeviceState, DeviceGridState destDeviceState) {
        boolean needsToMigrate = !destDeviceState.isCompatible(srcDeviceState);
        if (needsToMigrate) {
            Log.i(TAG, "Migration is needed. destDeviceState: " + destDeviceState
                    + ", srcDeviceState: " + srcDeviceState);
        }
        return needsToMigrate;
    }

    @VisibleForTesting
    public static List<DbEntry> readAllEntries(SQLiteDatabase db, String tableName,
            Context context) {
        DbReader dbReader = new DbReader(db, tableName, context, getValidPackages(context));
        List<DbEntry> result = dbReader.loadAllWorkspaceEntries();
        result.addAll(dbReader.loadHotseatEntries());
        return result;
    }

    /**
     * When migrating the grid, we copy the table
     * {@link LauncherSettings.Favorites#TABLE_NAME} from {@code source} into
     * {@link LauncherSettings.Favorites#TMP_TABLE}, run the grid size migration algorithm
     * to migrate the later to the former, and load the workspace from the default
     * {@link LauncherSettings.Favorites#TABLE_NAME}.
     *
     * @return false if the migration failed.
     */
    public static boolean migrateGridIfNeeded(
            @NonNull Context context,
            @NonNull DeviceGridState srcDeviceState,
            @NonNull DeviceGridState destDeviceState,
            @NonNull DatabaseHelper target,
            @NonNull SQLiteDatabase source) {
        if (!needsToMigrate(srcDeviceState, destDeviceState)) {
            return true;
        }

        if (Flags.enableGridMigrationFix()
                && srcDeviceState.getColumns().equals(destDeviceState.getColumns())
                && srcDeviceState.getRows() < destDeviceState.getRows()) {
            // Only use this strategy when comparing the previous grid to the new grid and the
            // columns are the same and the destination has more rows
            copyTable(source, TABLE_NAME, target.getWritableDatabase(), TABLE_NAME, context);
            destDeviceState.writeToPrefs(context);
            return true;
        }
        copyTable(source, TABLE_NAME, target.getWritableDatabase(), TMP_TABLE, context);

        HashSet<String> validPackages = getValidPackages(context);
        long migrationStartTime = System.currentTimeMillis();
        try (SQLiteTransaction t = new SQLiteTransaction(target.getWritableDatabase())) {
            DbReader srcReader = new DbReader(t.getDb(), TMP_TABLE, context, validPackages);
            DbReader destReader = new DbReader(t.getDb(), TABLE_NAME, context, validPackages);

            Point targetSize = new Point(destDeviceState.getColumns(), destDeviceState.getRows());
            migrate(target, srcReader, destReader, destDeviceState.getNumHotseat(),
                    targetSize, srcDeviceState, destDeviceState);
            dropTable(t.getDb(), TMP_TABLE);
            t.commit();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error during grid migration", e);

            return false;
        } finally {
            Log.v(TAG, "Workspace migration completed in "
                    + (System.currentTimeMillis() - migrationStartTime));

            // Save current configuration, so that the migration does not run again.
            destDeviceState.writeToPrefs(context);
        }
    }

    public static boolean migrate(
            @NonNull DatabaseHelper helper,
            @NonNull final DbReader srcReader, @NonNull final DbReader destReader,
            final int destHotseatSize, @NonNull final Point targetSize,
            @NonNull final DeviceGridState srcDeviceState,
            @NonNull final DeviceGridState destDeviceState) {

        final List<DbEntry> srcHotseatItems = srcReader.loadHotseatEntries();
        final List<DbEntry> srcWorkspaceItems = srcReader.loadAllWorkspaceEntries();
        final List<DbEntry> dstHotseatItems = destReader.loadHotseatEntries();
        final List<DbEntry> dstWorkspaceItems = destReader.loadAllWorkspaceEntries();
        final List<DbEntry> hotseatToBeAdded = new ArrayList<>(1);
        final List<DbEntry> workspaceToBeAdded = new ArrayList<>(1);
        final IntArray toBeRemoved = new IntArray();

        calcDiff(srcHotseatItems, dstHotseatItems, hotseatToBeAdded, toBeRemoved);
        calcDiff(srcWorkspaceItems, dstWorkspaceItems, workspaceToBeAdded, toBeRemoved);

        final int trgX = targetSize.x;
        final int trgY = targetSize.y;

        if (DEBUG) {
            Log.d(TAG, "Start migration:"
                    + "\n Source Device:"
                    + srcWorkspaceItems.stream().map(DbEntry::toString).collect(
                    Collectors.joining(",\n", "[", "]"))
                    + "\n Target Device:"
                    + dstWorkspaceItems.stream().map(DbEntry::toString).collect(
                    Collectors.joining(",\n", "[", "]"))
                    + "\n Removing Items:"
                    + dstWorkspaceItems.stream().filter(entry ->
                            toBeRemoved.contains(entry.id)).map(DbEntry::toString).collect(
                    Collectors.joining(",\n", "[", "]"))
                    + "\n Adding Workspace Items:"
                    + workspaceToBeAdded.stream().map(DbEntry::toString).collect(
                    Collectors.joining(",\n", "[", "]"))
                    + "\n Adding Hotseat Items:"
                    + hotseatToBeAdded.stream().map(DbEntry::toString).collect(
                    Collectors.joining(",\n", "[", "]"))
            );
        }
        if (!toBeRemoved.isEmpty()) {
            removeEntryFromDb(destReader.mDb, destReader.mTableName, toBeRemoved);
        }
        if (hotseatToBeAdded.isEmpty() && workspaceToBeAdded.isEmpty()) {
            return false;
        }

        // Sort the items by the reading order.
        Collections.sort(hotseatToBeAdded);
        Collections.sort(workspaceToBeAdded);

        // Migrate hotseat
        solveHotseatPlacement(helper, destHotseatSize,
                srcReader, destReader, dstHotseatItems, hotseatToBeAdded);

        // Migrate workspace.
        // First we create a collection of the screens
        List<Integer> screens = new ArrayList<>();
        for (int screenId = 0; screenId <= destReader.mLastScreenId; screenId++) {
            screens.add(screenId);
        }

        boolean preservePages = false;
        if (screens.isEmpty() && FeatureFlags.ENABLE_NEW_MIGRATION_LOGIC.get()) {
            preservePages = destDeviceState.compareTo(srcDeviceState) >= 0
                    && destDeviceState.getColumns() - srcDeviceState.getColumns() <= 2;
        }

        // Then we place the items on the screens
        for (int screenId : screens) {
            if (DEBUG) {
                Log.d(TAG, "Migrating " + screenId);
            }
            solveGridPlacement(helper, srcReader,
                    destReader, screenId, trgX, trgY, workspaceToBeAdded, false);
            if (workspaceToBeAdded.isEmpty()) {
                break;
            }
        }

        // In case the new grid is smaller, there might be some leftover items that don't fit on
        // any of the screens, in this case we add them to new screens until all of them are placed.
        int screenId = destReader.mLastScreenId + 1;
        while (!workspaceToBeAdded.isEmpty()) {
            solveGridPlacement(helper, srcReader,
                    destReader, screenId, trgX, trgY, workspaceToBeAdded, preservePages);
            screenId++;
        }

        return true;
    }

    /**
     * Calculate the differences between {@code src} (denoted by A) and {@code dest}
     * (denoted by B).
     * All DbEntry in A - B will be added to {@code toBeAdded}
     * All DbEntry.id in B - A will be added to {@code toBeRemoved}
     */
    private static void calcDiff(@NonNull final List<DbEntry> src,
            @NonNull final List<DbEntry> dest, @NonNull final List<DbEntry> toBeAdded,
            @NonNull final IntArray toBeRemoved) {
        src.forEach(entry -> {
            if (!dest.contains(entry)) {
                toBeAdded.add(entry);
            }
        });
        dest.forEach(entry -> {
            if (!src.contains(entry)) {
                toBeRemoved.add(entry.id);
                if (entry.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
                    entry.mFolderItems.values().forEach(ids -> ids.forEach(toBeRemoved::add));
                }
            }
        });
    }

    private static void insertEntryInDb(DatabaseHelper helper, DbEntry entry,
            String srcTableName, String destTableName) {
        int id = copyEntryAndUpdate(helper, entry, srcTableName, destTableName);

        if (entry.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER
                || entry.itemType == LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR) {
            for (Set<Integer> itemIds : entry.mFolderItems.values()) {
                for (int itemId : itemIds) {
                    copyEntryAndUpdate(helper, itemId, id, srcTableName, destTableName);
                }
            }
        }
    }

    private static int copyEntryAndUpdate(DatabaseHelper helper,
            DbEntry entry, String srcTableName, String destTableName) {
        return copyEntryAndUpdate(helper, entry, -1, -1, srcTableName, destTableName);
    }

    private static int copyEntryAndUpdate(DatabaseHelper helper,
            int id, int folderId, String srcTableName, String destTableName) {
        return copyEntryAndUpdate(helper, null, id, folderId, srcTableName, destTableName);
    }

    private static int copyEntryAndUpdate(DatabaseHelper helper, DbEntry entry,
            int id, int folderId, String srcTableName, String destTableName) {
        int newId = -1;
        Cursor c = helper.getWritableDatabase().query(srcTableName, null,
                LauncherSettings.Favorites._ID + " = '" + (entry != null ? entry.id : id) + "'",
                null, null, null, null);
        while (c.moveToNext()) {
            ContentValues values = new ContentValues();
            DatabaseUtils.cursorRowToContentValues(c, values);
            if (entry != null) {
                entry.updateContentValues(values);
            } else {
                values.put(LauncherSettings.Favorites.CONTAINER, folderId);
            }
            newId = helper.generateNewItemId();
            values.put(LauncherSettings.Favorites._ID, newId);
            helper.getWritableDatabase().insert(destTableName, null, values);
        }
        c.close();
        return newId;
    }

    private static void removeEntryFromDb(SQLiteDatabase db, String tableName, IntArray entryIds) {
        db.delete(tableName,
                Utilities.createDbSelectionQuery(LauncherSettings.Favorites._ID, entryIds), null);
    }

    private static HashSet<String> getValidPackages(Context context) {
        // Initialize list of valid packages. This contain all the packages which are already on
        // the device and packages which are being installed. Any item which doesn't belong to
        // this set is removed.
        // Since the loader removes such items anyway, removing these items here doesn't cause
        // any extra data loss and gives us more free space on the grid for better migration.
        HashSet<String> validPackages = new HashSet<>();
        for (PackageInfo info : context.getPackageManager()
                .getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES)) {
            validPackages.add(info.packageName);
        }
        InstallSessionHelper.INSTANCE.get(context)
                .getActiveSessions().keySet()
                .forEach(packageUserKey -> validPackages.add(packageUserKey.mPackageName));
        return validPackages;
    }

    private static void solveGridPlacement(@NonNull final DatabaseHelper helper,
            @NonNull final DbReader srcReader, @NonNull final DbReader destReader,
            final int screenId, final int trgX, final int trgY,
            @NonNull final List<DbEntry> sortedItemsToPlace, final boolean matchingScreenIdOnly) {
        final GridOccupancy occupied = new GridOccupancy(trgX, trgY);
        final Point trg = new Point(trgX, trgY);
        final Point next = new Point(0, screenId == 0
                && (FeatureFlags.QSB_ON_FIRST_SCREEN
                && (!ENABLE_SMARTSPACE_REMOVAL.get() || LauncherPrefs.getPrefs(destReader.mContext)
                .getBoolean(SMARTSPACE_ON_HOME_SCREEN, true))
                && !shouldShowFirstPageWidget())
                ? 1 /* smartspace */ : 0);
        List<DbEntry> existedEntries = destReader.mWorkspaceEntriesByScreenId.get(screenId);
        if (existedEntries != null) {
            for (DbEntry entry : existedEntries) {
                occupied.markCells(entry, true);
            }
        }
        Iterator<DbEntry> iterator = sortedItemsToPlace.iterator();
        while (iterator.hasNext()) {
            final DbEntry entry = iterator.next();
            if (matchingScreenIdOnly && entry.screenId < screenId) continue;
            if (matchingScreenIdOnly && entry.screenId > screenId) break;
            if (entry.minSpanX > trgX || entry.minSpanY > trgY) {
                iterator.remove();
                continue;
            }
            if (findPlacementForEntry(entry, next, trg, occupied, screenId)) {
                insertEntryInDb(helper, entry, srcReader.mTableName, destReader.mTableName);
                iterator.remove();
            }
        }
    }

    /**
     * Search for the next possible placement of an icon. (mNextStartX, mNextStartY) serves as
     * a memoization of last placement, we can start our search for next placement from there
     * to speed up the search.
     */
    private static boolean findPlacementForEntry(@NonNull final DbEntry entry,
            @NonNull final Point next, @NonNull final Point trg,
            @NonNull final GridOccupancy occupied, final int screenId) {
        for (int y = next.y; y <  trg.y; y++) {
            for (int x = next.x; x < trg.x; x++) {
                boolean fits = occupied.isRegionVacant(x, y, entry.spanX, entry.spanY);
                boolean minFits = occupied.isRegionVacant(x, y, entry.minSpanX,
                        entry.minSpanY);
                if (minFits) {
                    entry.spanX = entry.minSpanX;
                    entry.spanY = entry.minSpanY;
                }
                if (fits || minFits) {
                    entry.screenId = screenId;
                    entry.cellX = x;
                    entry.cellY = y;
                    occupied.markCells(entry, true);
                    next.set(x + entry.spanX, y);
                    return true;
                }
            }
            next.set(0, next.y);
        }
        return false;
    }

    private static void solveHotseatPlacement(
            @NonNull final DatabaseHelper helper, final int hotseatSize,
            @NonNull final DbReader srcReader, @NonNull final DbReader destReader,
            @NonNull final  List<DbEntry> placedHotseatItems,
            @NonNull final List<DbEntry> itemsToPlace) {

        final boolean[] occupied = new boolean[hotseatSize];
        for (DbEntry entry : placedHotseatItems) {
            occupied[entry.screenId] = true;
        }

        for (int i = 0; i < occupied.length; i++) {
            if (!occupied[i] && !itemsToPlace.isEmpty()) {
                DbEntry entry = itemsToPlace.remove(0);
                entry.screenId = i;
                // These values does not affect the item position, but we should set them
                // to something other than -1.
                entry.cellX = i;
                entry.cellY = 0;
                insertEntryInDb(helper, entry, srcReader.mTableName, destReader.mTableName);
                occupied[entry.screenId] = true;
            }
        }
    }

    protected static class DbReader {

        private final SQLiteDatabase mDb;
        private final String mTableName;
        private final Context mContext;
        private final Set<String> mValidPackages;
        private int mLastScreenId = -1;

        private final Map<Integer, ArrayList<DbEntry>> mWorkspaceEntriesByScreenId =
                new ArrayMap<>();

        DbReader(SQLiteDatabase db, String tableName, Context context,
                Set<String> validPackages) {
            mDb = db;
            mTableName = tableName;
            mContext = context;
            mValidPackages = validPackages;
        }

        protected List<DbEntry> loadHotseatEntries() {
            final List<DbEntry> hotseatEntries = new ArrayList<>();
            Cursor c = queryWorkspace(
                    new String[]{
                            LauncherSettings.Favorites._ID,                  // 0
                            LauncherSettings.Favorites.ITEM_TYPE,            // 1
                            LauncherSettings.Favorites.INTENT,               // 2
                            LauncherSettings.Favorites.SCREEN},              // 3
                    LauncherSettings.Favorites.CONTAINER + " = "
                            + LauncherSettings.Favorites.CONTAINER_HOTSEAT);

            final int indexId = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
            final int indexItemType = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
            final int indexIntent = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
            final int indexScreen = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);

            IntArray entriesToRemove = new IntArray();
            while (c.moveToNext()) {
                DbEntry entry = new DbEntry();
                entry.id = c.getInt(indexId);
                entry.itemType = c.getInt(indexItemType);
                entry.screenId = c.getInt(indexScreen);

                try {
                    // calculate weight
                    switch (entry.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION: {
                            entry.mIntent = c.getString(indexIntent);
                            verifyIntent(c.getString(indexIntent));
                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_FOLDER: {
                            int total = getFolderItemsCount(entry);
                            if (total == 0) {
                                throw new Exception("Folder is empty");
                            }
                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR: {
                            int total = getFolderItemsCount(entry);
                            if (total != 2) {
                                throw new Exception("App pair contains fewer or more than 2 items");
                            }
                            break;
                        }
                        default:
                            throw new Exception("Invalid item type");
                    }
                } catch (Exception e) {
                    if (DEBUG) {
                        Log.d(TAG, "Removing item " + entry.id, e);
                    }
                    entriesToRemove.add(entry.id);
                    continue;
                }
                hotseatEntries.add(entry);
            }
            removeEntryFromDb(mDb, mTableName, entriesToRemove);
            c.close();
            return hotseatEntries;
        }

        protected List<DbEntry> loadAllWorkspaceEntries() {
            final List<DbEntry> workspaceEntries = new ArrayList<>();
            Cursor c = queryWorkspace(
                    new String[]{
                            LauncherSettings.Favorites._ID,                  // 0
                            LauncherSettings.Favorites.ITEM_TYPE,            // 1
                            LauncherSettings.Favorites.SCREEN,               // 2
                            LauncherSettings.Favorites.CELLX,                // 3
                            LauncherSettings.Favorites.CELLY,                // 4
                            LauncherSettings.Favorites.SPANX,                // 5
                            LauncherSettings.Favorites.SPANY,                // 6
                            LauncherSettings.Favorites.INTENT,               // 7
                            LauncherSettings.Favorites.APPWIDGET_PROVIDER,   // 8
                            LauncherSettings.Favorites.APPWIDGET_ID},        // 9
                        LauncherSettings.Favorites.CONTAINER + " = "
                            + LauncherSettings.Favorites.CONTAINER_DESKTOP);
            final int indexId = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
            final int indexItemType = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
            final int indexScreen = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
            final int indexCellX = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
            final int indexCellY = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
            final int indexSpanX = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANX);
            final int indexSpanY = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANY);
            final int indexIntent = c.getColumnIndexOrThrow(LauncherSettings.Favorites.INTENT);
            final int indexAppWidgetProvider = c.getColumnIndexOrThrow(
                    LauncherSettings.Favorites.APPWIDGET_PROVIDER);
            final int indexAppWidgetId = c.getColumnIndexOrThrow(
                    LauncherSettings.Favorites.APPWIDGET_ID);

            IntArray entriesToRemove = new IntArray();
            WidgetManagerHelper widgetManagerHelper = new WidgetManagerHelper(mContext);
            while (c.moveToNext()) {
                DbEntry entry = new DbEntry();
                entry.id = c.getInt(indexId);
                entry.itemType = c.getInt(indexItemType);
                entry.screenId = c.getInt(indexScreen);
                mLastScreenId = Math.max(mLastScreenId, entry.screenId);
                entry.cellX = c.getInt(indexCellX);
                entry.cellY = c.getInt(indexCellY);
                entry.spanX = c.getInt(indexSpanX);
                entry.spanY = c.getInt(indexSpanY);

                try {
                    // calculate weight
                    switch (entry.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION: {
                            entry.mIntent = c.getString(indexIntent);
                            verifyIntent(entry.mIntent);
                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET: {
                            entry.mProvider = c.getString(indexAppWidgetProvider);
                            ComponentName cn = ComponentName.unflattenFromString(entry.mProvider);
                            verifyPackage(cn.getPackageName());

                            int widgetId = c.getInt(indexAppWidgetId);
                            LauncherAppWidgetProviderInfo pInfo = widgetManagerHelper
                                    .getLauncherAppWidgetInfo(widgetId, cn);
                            Point spans = null;
                            if (pInfo != null) {
                                spans = pInfo.getMinSpans();
                            }
                            if (spans != null) {
                                entry.minSpanX = spans.x > 0 ? spans.x : entry.spanX;
                                entry.minSpanY = spans.y > 0 ? spans.y : entry.spanY;
                            } else {
                                // Assume that the widget be resized down to 2x2
                                entry.minSpanX = entry.minSpanY = 2;
                            }

                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_FOLDER: {
                            int total = getFolderItemsCount(entry);
                            if (total == 0) {
                                throw new Exception("Folder is empty");
                            }
                            break;
                        }
                        case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR: {
                            int total = getFolderItemsCount(entry);
                            if (total != 2) {
                                throw new Exception("App pair contains fewer or more than 2 items");
                            }
                            break;
                        }
                        default:
                            throw new Exception("Invalid item type");
                    }
                } catch (Exception e) {
                    if (DEBUG) {
                        Log.d(TAG, "Removing item " + entry.id, e);
                    }
                    entriesToRemove.add(entry.id);
                    continue;
                }
                workspaceEntries.add(entry);
                if (!mWorkspaceEntriesByScreenId.containsKey(entry.screenId)) {
                    mWorkspaceEntriesByScreenId.put(entry.screenId, new ArrayList<>());
                }
                mWorkspaceEntriesByScreenId.get(entry.screenId).add(entry);
            }
            removeEntryFromDb(mDb, mTableName, entriesToRemove);
            c.close();
            return workspaceEntries;
        }

        private int getFolderItemsCount(DbEntry entry) {
            Cursor c = queryWorkspace(
                    new String[]{LauncherSettings.Favorites._ID, LauncherSettings.Favorites.INTENT},
                    LauncherSettings.Favorites.CONTAINER + " = " + entry.id);

            int total = 0;
            while (c.moveToNext()) {
                try {
                    int id = c.getInt(0);
                    String intent = c.getString(1);
                    verifyIntent(intent);
                    total++;
                    if (!entry.mFolderItems.containsKey(intent)) {
                        entry.mFolderItems.put(intent, new HashSet<>());
                    }
                    entry.mFolderItems.get(intent).add(id);
                } catch (Exception e) {
                    removeEntryFromDb(mDb, mTableName, IntArray.wrap(c.getInt(0)));
                }
            }
            c.close();
            return total;
        }

        private Cursor queryWorkspace(String[] columns, String where) {
            return mDb.query(mTableName, columns, where, null, null, null, null);
        }

        /** Verifies if the mIntent should be restored. */
        private void verifyIntent(String intentStr)
                throws Exception {
            Intent intent = Intent.parseUri(intentStr, 0);
            if (intent.getComponent() != null) {
                verifyPackage(intent.getComponent().getPackageName());
            } else if (intent.getPackage() != null) {
                // Only verify package if the component was null.
                verifyPackage(intent.getPackage());
            }
        }

        /** Verifies if the package should be restored */
        private void verifyPackage(String packageName)
                throws Exception {
            if (!mValidPackages.contains(packageName)) {
                // TODO(b/151468819): Handle promise app icon restoration during grid migration.
                throw new Exception("Package not available");
            }
        }
    }

    public static class DbEntry extends ItemInfo implements Comparable<DbEntry> {

        private String mIntent;
        private String mProvider;
        private Map<String, Set<Integer>> mFolderItems = new HashMap<>();

        /** Comparator according to the reading order */
        @Override
        public int compareTo(DbEntry another) {
            if (screenId != another.screenId) {
                return Integer.compare(screenId, another.screenId);
            }
            if (cellY != another.cellY) {
                return Integer.compare(cellY, another.cellY);
            }
            return Integer.compare(cellX, another.cellX);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DbEntry entry = (DbEntry) o;
            return Objects.equals(getEntryMigrationId(), entry.getEntryMigrationId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getEntryMigrationId());
        }

        public void updateContentValues(ContentValues values) {
            values.put(LauncherSettings.Favorites.SCREEN, screenId);
            values.put(LauncherSettings.Favorites.CELLX, cellX);
            values.put(LauncherSettings.Favorites.CELLY, cellY);
            values.put(LauncherSettings.Favorites.SPANX, spanX);
            values.put(LauncherSettings.Favorites.SPANY, spanY);
        }

        /** This id is not used in the DB is only used while doing the migration and it identifies
         * an entry on each workspace. For example two calculator icons would have the same
         * migration id even thought they have different database ids.
         */
        public String getEntryMigrationId() {
            switch (itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR:
                    return getFolderMigrationId();
                case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                    return mProvider;
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                    final String intentStr = cleanIntentString(mIntent);
                    try {
                        Intent i = Intent.parseUri(intentStr, 0);
                        return Objects.requireNonNull(i.getComponent()).toString();
                    } catch (Exception e) {
                        return intentStr;
                    }
                default:
                    return cleanIntentString(mIntent);
            }
        }

        /**
         * This method should return an id that should be the same for two folders containing the
         * same elements.
         */
        @NonNull
        private String getFolderMigrationId() {
            return mFolderItems.keySet().stream()
                    .map(intentString -> mFolderItems.get(intentString).size()
                            + cleanIntentString(intentString))
                    .sorted()
                    .collect(Collectors.joining(","));
        }

        /**
         * This is needed because sourceBounds can change and make the id of two equal items
         * different.
         */
        @NonNull
        private String cleanIntentString(@NonNull String intentStr) {
            try {
                Intent i = Intent.parseUri(intentStr, 0);
                i.setSourceBounds(null);
                return i.toURI();
            } catch (URISyntaxException e) {
                Log.e(TAG, "Unable to parse Intent string", e);
                return intentStr;
            }

        }
    }
}
