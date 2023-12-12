/*
 * Copyright 2023 The Android Open Source Project
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


package com.android.quickstep.util;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_PAIR_LAUNCH;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.common.split.SplitScreenConstants.isPersistentSnapPosition;

import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.common.split.SplitScreenConstants.PersistentSnapPosition;

import java.util.Arrays;

/**
 * Controller class that handles app pair interactions: saving, modifying, deleting, etc.
 * <br>
 * App pairs contain two "member" apps, which are determined at the time of app pair creation
 * and never modified. The member apps are WorkspaceItemInfos, but use the "rank" attribute
 * differently from other ItemInfos -- we use it to store information about the split position and
 * ratio.
 */
public class AppPairsController {
    // Used for encoding and decoding the "rank" attribute
    private static final int BITMASK_SIZE = 16;
    private static final int BITMASK_FOR_SNAP_POSITION = (1 << BITMASK_SIZE) - 1;

    private Context mContext;
    private final SplitSelectStateController mSplitSelectStateController;
    private final StatsLogManager mStatsLogManager;
    public AppPairsController(Context context,
            SplitSelectStateController splitSelectStateController,
            StatsLogManager statsLogManager) {
        mContext = context;
        mSplitSelectStateController = splitSelectStateController;
        mStatsLogManager = statsLogManager;
    }

    void onDestroy() {
        mContext = null;
    }

    /**
     * Creates a new app pair ItemInfo and adds it to the workspace
     */
    public void saveAppPair(GroupedTaskView gtv) {
        TaskView.TaskIdAttributeContainer[] attributes = gtv.getTaskIdAttributeContainers();
        WorkspaceItemInfo app1 = attributes[0].getItemInfo().clone();
        WorkspaceItemInfo app2 = attributes[1].getItemInfo().clone();
        app1.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        app2.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;

        @PersistentSnapPosition int snapPosition = gtv.getSnapPosition();
        if (!isPersistentSnapPosition(snapPosition)) {
            throw new RuntimeException("tried to save an app pair with illegal snapPosition");
        }

        app1.rank = encodeRank(SPLIT_POSITION_TOP_OR_LEFT, snapPosition);
        app2.rank = encodeRank(SPLIT_POSITION_BOTTOM_OR_RIGHT, snapPosition);
        FolderInfo newAppPair = FolderInfo.createAppPair(app1, app2);

        IconCache iconCache = LauncherAppState.getInstance(mContext).getIconCache();
        MODEL_EXECUTOR.execute(() -> {
            newAppPair.contents.forEach(member -> {
                member.title = "";
                member.bitmap = iconCache.getDefaultIcon(newAppPair.user);
                iconCache.getTitleAndIcon(member, member.usingLowResIcon());
            });
            newAppPair.title = getDefaultTitle(newAppPair.contents.get(0).title,
                    newAppPair.contents.get(1).title);
            MAIN_EXECUTOR.execute(() -> {
                LauncherAccessibilityDelegate delegate =
                        Launcher.getLauncher(mContext).getAccessibilityDelegate();
                if (delegate != null) {
                    delegate.addToWorkspace(newAppPair, true);
                    mStatsLogManager.logger().withItemInfo(newAppPair)
                            .log(StatsLogManager.LauncherEvent.LAUNCHER_APP_PAIR_SAVE);
                }
            });
        });
    }

    /**
     * Launches an app pair by searching the RecentsModel for running instances of each app, and
     * staging either those running instances or launching the apps as new Intents.
     */
    public void launchAppPair(AppPairIcon appPairIcon) {
        WorkspaceItemInfo app1 = appPairIcon.getInfo().contents.get(0);
        WorkspaceItemInfo app2 = appPairIcon.getInfo().contents.get(1);
        ComponentKey app1Key = new ComponentKey(app1.getTargetComponent(), app1.user);
        ComponentKey app2Key = new ComponentKey(app2.getTargetComponent(), app2.user);
        mSplitSelectStateController.findLastActiveTasksAndRunCallback(
                Arrays.asList(app1Key, app2Key),
                false /* findExactPairMatch */,
                foundTasks -> {
                    @Nullable Task foundTask1 = foundTasks.get(0);
                    Intent task1Intent;
                    int task1Id;
                    if (foundTask1 != null) {
                        task1Id = foundTask1.key.id;
                        task1Intent = null;
                    } else {
                        task1Id = ActivityTaskManager.INVALID_TASK_ID;
                        task1Intent = app1.intent;
                    }

                    mSplitSelectStateController.setInitialTaskSelect(task1Intent,
                            AppPairsController.convertRankToStagePosition(app1.rank),
                            app1,
                            LAUNCHER_APP_PAIR_LAUNCH,
                            task1Id);

                    @Nullable Task foundTask2 = foundTasks.get(1);
                    if (foundTask2 != null) {
                        mSplitSelectStateController.setSecondTask(foundTask2);
                    } else {
                        mSplitSelectStateController.setSecondTask(
                                app2.intent, app2.user);
                    }

                    mSplitSelectStateController.setLaunchingIconView(appPairIcon);

                    mSplitSelectStateController.launchSplitTasks(
                            AppPairsController.convertRankToSnapPosition(app1.rank));
                }
        );
    }

    /**
     * App pair members have a "rank" attribute that contains information about the split position
     * and ratio. We implement this by splitting the int in half (e.g. 16 bits each), then use one
     * half to store splitPosition (left vs right) and the other half to store snapPosition
     * (30-70 split vs 50-50 split)
     */
    @VisibleForTesting
    public int encodeRank(int splitPosition, int snapPosition) {
        return (splitPosition << BITMASK_SIZE) + snapPosition;
    }

    /**
     * Returns the desired stage position for the app pair to be launched in (decoded from the
     * "rank" integer).
     */
    public static int convertRankToStagePosition(int rank) {
        return rank >> BITMASK_SIZE;
    }

    /**
     * Returns the desired split ratio for the app pair to be launched in (decoded from the "rank"
     * integer).
     */
    public static int convertRankToSnapPosition(int rank) {
        return rank & BITMASK_FOR_SNAP_POSITION;
    }

    /**
     * Returns a formatted default title for the app pair.
     */
    public String getDefaultTitle(CharSequence app1, CharSequence app2) {
        return mContext.getString(R.string.app_pair_default_title, app1, app2);
    }
}
