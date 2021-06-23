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
package com.android.launcher3.hybridhotseat;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent
        .LAUNCHER_HOTSEAT_EDU_ONLY_TIP;

import android.content.Intent;
import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.views.ArrowTipView;
import com.android.launcher3.views.Snackbar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Controller class for managing user onboaridng flow for hybrid hotseat
 */
public class HotseatEduController {

    public static final String SETTINGS_ACTION =
            "android.settings.ACTION_CONTENT_SUGGESTIONS_SETTINGS";

    private final Launcher mLauncher;
    private final Hotseat mHotseat;
    private List<WorkspaceItemInfo> mPredictedApps;
    private HotseatEduDialog mActiveDialog;

    private ArrayList<ItemInfo> mNewItems = new ArrayList<>();
    private IntArray mNewScreens = null;

    HotseatEduController(Launcher launcher) {
        mLauncher = launcher;
        mHotseat = launcher.getHotseat();
    }

    /**
     * Checks what type of migration should be used and migrates hotseat
     */
    void migrate() {
        HotseatRestoreHelper.createBackup(mLauncher);
        if (FeatureFlags.HOTSEAT_MIGRATE_TO_FOLDER.get()) {
            migrateToFolder();
        } else {
            migrateHotseatWhole();
        }
        Snackbar.show(mLauncher, R.string.hotsaet_tip_prediction_enabled,
                R.string.hotseat_prediction_settings, null,
                () -> mLauncher.startActivity(getSettingsIntent()));
    }

    /**
     * This migration places all non folder items in the hotseat into a folder and then moves
     * all folders in the hotseat to a workspace page that has enough empty spots.
     *
     * @return pageId that has accepted the items.
     */
    private int migrateToFolder() {
        ArrayDeque<FolderInfo> folders = new ArrayDeque<>();
        ArrayList<WorkspaceItemInfo> putIntoFolder = new ArrayList<>();

        //separate folders and items that can get in folders
        for (int i = 0; i < mLauncher.getDeviceProfile().numShownHotseatIcons; i++) {
            View view = mHotseat.getChildAt(i, 0);
            if (view == null) continue;
            ItemInfo info = (ItemInfo) view.getTag();
            if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
                folders.add((FolderInfo) info);
            } else if (info instanceof WorkspaceItemInfo && info.container == LauncherSettings
                    .Favorites.CONTAINER_HOTSEAT) {
                putIntoFolder.add((WorkspaceItemInfo) info);
            }
        }

        // create a temp folder and add non folder items to it
        if (!putIntoFolder.isEmpty()) {
            ItemInfo firstItem = putIntoFolder.get(0);
            FolderInfo folderInfo = new FolderInfo();
            mLauncher.getModelWriter().addItemToDatabase(folderInfo, firstItem.container,
                    firstItem.screenId, firstItem.cellX, firstItem.cellY);
            folderInfo.setTitle("", mLauncher.getModelWriter());
            folderInfo.contents.addAll(putIntoFolder);
            for (int i = 0; i < folderInfo.contents.size(); i++) {
                ItemInfo item = folderInfo.contents.get(i);
                item.rank = i;
                mLauncher.getModelWriter().moveItemInDatabase(item, folderInfo.id, 0,
                        item.cellX, item.cellY);
            }
            folders.add(folderInfo);
        }
        mNewItems.addAll(folders);

        return placeFoldersInWorkspace(folders);
    }

    private int placeFoldersInWorkspace(ArrayDeque<FolderInfo> folders) {
        if (folders.isEmpty()) return 0;

        Workspace workspace = mLauncher.getWorkspace();
        InvariantDeviceProfile idp = mLauncher.getDeviceProfile().inv;

        GridOccupancy[] occupancyList = new GridOccupancy[workspace.getChildCount()];
        for (int i = 0; i < occupancyList.length; i++) {
            occupancyList[i] = ((CellLayout) workspace.getChildAt(i)).cloneGridOccupancy();
        }
        //scan every screen to find available spots to place folders
        int occupancyIndex = 0;
        int[] itemXY = new int[2];
        while (occupancyIndex < occupancyList.length && !folders.isEmpty()) {
            GridOccupancy occupancy = occupancyList[occupancyIndex];
            if (occupancy.findVacantCell(itemXY, 1, 1)) {
                FolderInfo info = folders.poll();
                mLauncher.getModelWriter().moveItemInDatabase(info,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP,
                        workspace.getScreenIdForPageIndex(occupancyIndex), itemXY[0], itemXY[1]);
                occupancy.markCells(info, true);
            } else {
                occupancyIndex++;
            }
        }
        if (folders.isEmpty()) return workspace.getScreenIdForPageIndex(occupancyIndex);
        int screenId = LauncherSettings.Settings.call(mLauncher.getContentResolver(),
                LauncherSettings.Settings.METHOD_NEW_SCREEN_ID)
                .getInt(LauncherSettings.Settings.EXTRA_VALUE);
        // if all screens are full and we still have folders left, put those on a new page
        FolderInfo folderInfo;
        int col = 0;
        while ((folderInfo = folders.poll()) != null) {
            mLauncher.getModelWriter().moveItemInDatabase(folderInfo,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId, col++,
                    idp.numRows - 1);
        }
        mNewScreens = IntArray.wrap(screenId);
        return workspace.getPageCount();
    }

    /**
     * This migration option attempts to move the entire hotseat up to the first workspace that
     * has space to host items. If no such page is found, it moves items to a new page.
     *
     * @return pageId where items are migrated
     */
    private int migrateHotseatWhole() {
        Workspace workspace = mLauncher.getWorkspace();

        int pageId = -1;
        int toRow = 0;
        for (int i = 0; i < workspace.getPageCount(); i++) {
            CellLayout target = workspace.getScreenWithId(workspace.getScreenIdForPageIndex(i));
            if (target.makeSpaceForHotseatMigration(true)) {
                toRow = mLauncher.getDeviceProfile().inv.numRows - 1;
                pageId = i;
                break;
            }
        }
        if (pageId == -1) {
            pageId = LauncherSettings.Settings.call(mLauncher.getContentResolver(),
                    LauncherSettings.Settings.METHOD_NEW_SCREEN_ID)
                    .getInt(LauncherSettings.Settings.EXTRA_VALUE);
            mNewScreens = IntArray.wrap(pageId);
        }
        for (int i = 0; i < mLauncher.getDeviceProfile().numShownHotseatIcons; i++) {
            View child = mHotseat.getChildAt(i, 0);
            if (child == null || child.getTag() == null) continue;
            ItemInfo tag = (ItemInfo) child.getTag();
            if (tag.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) continue;
            mLauncher.getModelWriter().moveItemInDatabase(tag,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP, pageId, i, toRow);
            mNewItems.add(tag);
        }
        return pageId;
    }

    void moveHotseatItems() {
        mHotseat.removeAllViewsInLayout();
        if (!mNewItems.isEmpty()) {
            int lastPage = mNewItems.get(mNewItems.size() - 1).screenId;
            ArrayList<ItemInfo> animated = new ArrayList<>();
            ArrayList<ItemInfo> nonAnimated = new ArrayList<>();

            for (ItemInfo info : mNewItems) {
                if (info.screenId == lastPage) {
                    animated.add(info);
                } else {
                    nonAnimated.add(info);
                }
            }
            mLauncher.bindAppsAdded(mNewScreens, nonAnimated, animated);
        }
    }

    void finishOnboarding() {
        mLauncher.getModel().onWorkspaceUiChanged();
    }

    void showDimissTip() {
        if (mHotseat.getShortcutsAndWidgets().getChildCount()
                < mLauncher.getDeviceProfile().numShownHotseatIcons) {
            Snackbar.show(mLauncher, R.string.hotseat_tip_gaps_filled,
                    R.string.hotseat_prediction_settings, null,
                    () -> mLauncher.startActivity(getSettingsIntent()));
        } else {
            new ArrowTipView(mLauncher).show(
                    mLauncher.getString(R.string.hotseat_tip_no_empty_slots), mHotseat.getTop());
        }
    }

    void setPredictedApps(List<WorkspaceItemInfo> predictedApps) {
        mPredictedApps = predictedApps;
    }

    void showEdu() {
        int childCount = mHotseat.getShortcutsAndWidgets().getChildCount();
        CellLayout cellLayout = mLauncher.getWorkspace().getScreenWithId(Workspace.FIRST_SCREEN_ID);
        // hotseat is already empty and does not require migration. show edu tip
        boolean requiresMigration = IntStream.range(0, childCount).anyMatch(i -> {
            View v = mHotseat.getShortcutsAndWidgets().getChildAt(i);
            return v != null && v.getTag() != null && ((ItemInfo) v.getTag()).container
                    != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
        });
        boolean canMigrateToFirstPage = cellLayout.makeSpaceForHotseatMigration(false);
        if (requiresMigration && canMigrateToFirstPage) {
            showDialog();
        } else {
            new ArrowTipView(mLauncher).show(mLauncher.getString(
                    requiresMigration ? R.string.hotseat_tip_no_empty_slots
                            : R.string.hotseat_auto_enrolled),
                    mHotseat.getTop());
            mLauncher.getStatsLogManager().logger().log(LAUNCHER_HOTSEAT_EDU_ONLY_TIP);
            finishOnboarding();
        }
    }

    void showDialog() {
        if (mPredictedApps == null || mPredictedApps.isEmpty()) {
            return;
        }
        if (mActiveDialog != null) {
            mActiveDialog.handleClose(false);
        }
        mActiveDialog = HotseatEduDialog.getDialog(mLauncher);
        mActiveDialog.setHotseatEduController(this);
        mActiveDialog.show(mPredictedApps);
    }

    static Intent getSettingsIntent() {
        return new Intent(SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
