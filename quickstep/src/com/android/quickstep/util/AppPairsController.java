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

import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;

import java.util.Arrays;

/**
 * Mini controller class that handles app pair interactions: saving, modifying, deleting, etc.
 */
public class AppPairsController {

    private static final int POINT_THREE_RATIO = 0;
    private static final int POINT_FIVE_RATIO = 1;
    private static final int POINT_SEVEN_RATIO = 2;
    /**
     * Used to calculate {@link #complement(int)}
     */
    private static final int FULL_RATIO = 2;

    private static final int LEFT_TOP = 0;
    private static final int RIGHT_BOTTOM = 1 << 2;

    // TODO (jeremysim b/274189428): Support saving different ratios in future.
    public int DEFAULT_RATIO = POINT_FIVE_RATIO;

    private final Context mContext;
    private final SplitSelectStateController mSplitSelectStateController;
    private final StatsLogManager mStatsLogManager;
    public AppPairsController(Context context,
            SplitSelectStateController splitSelectStateController,
            StatsLogManager statsLogManager) {
        mContext = context;
        mSplitSelectStateController = splitSelectStateController;
        mStatsLogManager = statsLogManager;
    }

    /**
     * Creates a new app pair ItemInfo and adds it to the workspace
     */
    public void saveAppPair(TaskView taskView) {
        TaskView.TaskIdAttributeContainer[] attributes = taskView.getTaskIdAttributeContainers();
        WorkspaceItemInfo app1 = attributes[0].getItemInfo().clone();
        WorkspaceItemInfo app2 = attributes[1].getItemInfo().clone();
        app1.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        app2.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        app1.rank = DEFAULT_RATIO + LEFT_TOP;
        app2.rank = complement(DEFAULT_RATIO) + RIGHT_BOTTOM;
        FolderInfo newAppPair = FolderInfo.createAppPair(app1, app2);
        // TODO (jeremysim b/274189428): Generate default title here.
        newAppPair.title = "App pair 1";

        IconCache iconCache = LauncherAppState.getInstance(mContext).getIconCache();
        MODEL_EXECUTOR.execute(() -> {
            newAppPair.contents.forEach(member -> {
                member.title = "";
                member.bitmap = iconCache.getDefaultIcon(newAppPair.user);
                iconCache.getTitleAndIcon(member, member.usingLowResIcon());
            });
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
    public void launchAppPair(WorkspaceItemInfo app1, WorkspaceItemInfo app2) {
        ComponentKey app1Key = new ComponentKey(app1.getTargetComponent(), app1.user);
        ComponentKey app2Key = new ComponentKey(app2.getTargetComponent(), app2.user);
        mSplitSelectStateController.findLastActiveTasksAndRunCallback(
                Arrays.asList(app1Key, app2Key),
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
                            SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT,
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

                    mSplitSelectStateController.launchSplitTasks();
                });
    }

    /**
     * Used to calculate the "opposite" side of the split ratio, so we can know how big the split
     * apps are supposed to be. This math works because POINT_THREE_RATIO is internally represented
     * by 0, POINT_FIVE_RATIO is represented by 1, and POINT_SEVEN_RATIO is represented by 2. There
     * are no other supported ratios for now.
     */
    private int complement(int ratio1) {
        int ratio2 = FULL_RATIO - ratio1;
        return ratio2;
    }
}
