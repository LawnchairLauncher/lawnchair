/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.model;

import static ch.deletescape.lawnchair.settings.ui.SettingsActivity.ALLOW_OVERLAP_PREF;
import static ch.deletescape.lawnchair.settings.ui.SettingsActivity.SMARTSPACE_PREF;

import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.util.Log;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherSettings.Settings;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.GridSizeMigrationTask;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.widget.custom.CustomWidgetParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class HomeWidgetMigrationTask extends GridSizeMigrationTask {

    public static final String PREF_MIGRATION_STATUS = "pref_migratedSmartspace";

    private final Context mContext;
    private final int mTrgX, mTrgY;

    private HomeWidgetMigrationTask(Context context,
            InvariantDeviceProfile idp,
            HashSet<String> validPackages,
            Point size) {
        super(context, idp, validPackages, size, size);

        mContext = context;

        mTrgX = size.x;
        mTrgY = size.y;
    }

    @Override
    protected boolean migrateWorkspace() throws Exception {
        ArrayList<Long> allScreens = LauncherModel.loadWorkspaceScreensDb(mContext);
        if (allScreens.isEmpty()) {
            throw new Exception("Unable to get workspace screens");
        }

        boolean allowOverlap = Utilities.getPrefs(mContext)
                .getBoolean(ALLOW_OVERLAP_PREF, false);
        GridOccupancy occupied = new GridOccupancy(mTrgX, mTrgY);

        if (!allowOverlap) {
            ArrayList<DbEntry> firstScreenItems = new ArrayList<>();
            for (long screenId : allScreens) {
                ArrayList<DbEntry> items = loadWorkspaceEntries(screenId);
                if (screenId == Workspace.FIRST_SCREEN_ID) {
                    firstScreenItems.addAll(items);
                    break;
                }
            }

            for (DbEntry item : firstScreenItems) {
                occupied.markCells(item, true);
            }
        }

        if (allowOverlap || occupied.isRegionVacant(0, 0, mTrgX, 1)) {
            List<LauncherAppWidgetProviderInfo> customWidgets =
                    CustomWidgetParser.getCustomWidgets(mContext);
            if (!customWidgets.isEmpty()) {
                LauncherAppWidgetProviderInfo provider = customWidgets.get(0);
                int widgetId = CustomWidgetParser
                        .getWidgetIdForCustomProvider(mContext, provider.provider);
                long itemId = LauncherSettings.Settings.call(mContext.getContentResolver(),
                        Settings.METHOD_NEW_ITEM_ID)
                        .getLong(LauncherSettings.Settings.EXTRA_VALUE);

                ContentValues values = new ContentValues();
                values.put(Favorites._ID, itemId);
                values.put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP);
                values.put(Favorites.SCREEN, Workspace.FIRST_SCREEN_ID);
                values.put(Favorites.CELLX, 0);
                values.put(Favorites.CELLY, 0);
                values.put(Favorites.SPANX, mTrgX);
                values.put(Favorites.SPANY, 1);
                values.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_CUSTOM_APPWIDGET);
                values.put(Favorites.APPWIDGET_ID, widgetId);
                values.put(Favorites.APPWIDGET_PROVIDER, provider.provider.flattenToString());
                mUpdateOperations.add(ContentProviderOperation
                        .newInsert(Favorites.CONTENT_URI).withValues(values).build());
            }
        }

        return applyOperations();
    }

    @SuppressLint("ApplySharedPref")
    public static void migrateIfNeeded(Context context) {
        SharedPreferences prefs = Utilities.getPrefs(context);

        boolean needsMigration = !prefs.getBoolean(PREF_MIGRATION_STATUS, false)
                && prefs.getBoolean(SMARTSPACE_PREF, true);
        if (!needsMigration) return;
        // Save the pref so we only run migration once
        prefs.edit().putBoolean(PREF_MIGRATION_STATUS, true).commit();

        HashSet<String> validPackages = getValidPackages(context);

        InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
        Point size = new Point(idp.numColumns, idp.numRows);

        try {
            if (!new HomeWidgetMigrationTask(context, LauncherAppState.getIDP(context),
                    validPackages, size).migrateWorkspace()) {
                throw new RuntimeException("Failed to migrate Smartspace");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
