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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_EDU_ONLY_TIP;

import android.content.Intent;
import android.graphics.Rect;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.views.ArrowTipView;
import com.android.launcher3.views.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Controller class for managing user onboaridng flow for hybrid hotseat
 */
public class HotseatEduController {

    private static final String TAG = "HotseatEduController";

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
        migrateHotseatWhole();
        Snackbar.show(mLauncher, R.string.hotsaet_tip_prediction_enabled,
                R.string.hotseat_prediction_settings, null,
                () -> mLauncher.startActivity(getSettingsIntent()));
    }

    /**
     * This migration option attempts to move the entire hotseat up to the first workspace that
     * has space to host items. If no such page is found, it moves items to a new page.
     *
     * @return pageId where items are migrated
     */
    private int migrateHotseatWhole() {
        Workspace<?> workspace = mLauncher.getWorkspace();

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
            pageId = mLauncher.getModel().getModelDbController().getNewScreenId();
            mNewScreens = IntArray.wrap(pageId);
        }
        boolean isPortrait = !mLauncher.getDeviceProfile().isVerticalBarLayout();
        int hotseatItemsNum = mLauncher.getDeviceProfile().numShownHotseatIcons;
        for (int i = 0; i < hotseatItemsNum; i++) {
            int x = isPortrait ? i : 0;
            int y = isPortrait ? 0 : hotseatItemsNum - i - 1;
            View child = mHotseat.getChildAt(x, y);
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
            showHotseatArrowTip(true, mLauncher.getString(R.string.hotseat_tip_no_empty_slots));
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
            if (showHotseatArrowTip(requiresMigration, mLauncher.getString(
                    requiresMigration ? R.string.hotseat_tip_no_empty_slots
                            : R.string.hotseat_auto_enrolled))) {
                mLauncher.getStatsLogManager().logger().log(LAUNCHER_HOTSEAT_EDU_ONLY_TIP);
            }
            finishOnboarding();
        }
    }

    /**
     * Finds a child suitable child in hotseat and shows arrow tip pointing at it.
     *
     * @param usePinned used to determine target view. If true, will use the first matching pinned
     *                  item. Otherwise, will use the first predicted child
     * @param message   String to be shown inside the arrowView
     * @return whether suitable child was found and tip was shown
     */
    private boolean showHotseatArrowTip(boolean usePinned, String message) {
        int childCount = mHotseat.getShortcutsAndWidgets().getChildCount();
        boolean isPortrait = !mLauncher.getDeviceProfile().isVerticalBarLayout();

        BubbleTextView tipTargetView = null;
        for (int i = childCount - 1; i > -1; i--) {
            int x = isPortrait ? i : 0;
            int y = isPortrait ? 0 : i;
            View v = mHotseat.getShortcutsAndWidgets().getChildAt(x, y);
            if (v instanceof BubbleTextView && v.getTag() instanceof WorkspaceItemInfo) {
                ItemInfo info = (ItemInfo) v.getTag();
                boolean isPinned = info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
                if (isPinned == usePinned) {
                    tipTargetView = (BubbleTextView) v;
                    break;
                }
            }
        }
        if (tipTargetView == null) {
            Log.e(TAG, "Unable to find suitable view for ArrowTip");
            return false;
        }
        Rect bounds = Utilities.getViewBounds(tipTargetView);
        new ArrowTipView(mLauncher).show(message, Gravity.END, bounds.centerX(), bounds.top);
        return true;
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
