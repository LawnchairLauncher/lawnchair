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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import static com.android.internal.jank.Cuj.CUJ_LAUNCHER_LAUNCH_APP_PAIR_FROM_TASKBAR;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_PAIR_LAUNCH;
import static com.android.launcher3.model.data.AppInfo.PACKAGE_KEY_COMPARATOR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.systemui.shared.recents.utilities.Utilities.isFreeformTask;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_NONE;
import static com.android.wm.shell.shared.split.SplitScreenConstants.getIndex;
import static com.android.wm.shell.shared.split.SplitScreenConstants.isPersistentSnapPosition;

import static java.util.stream.Collectors.toList;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.jank.Cuj;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.TaskViewItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.TaskContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;
import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Controller class that handles app pair interactions: saving, modifying, deleting, etc.
 * <br>
 * App pairs contain two "member" apps, which are determined at the time of app pair creation
 * and never modified. The member apps are WorkspaceItemInfos, but use the "rank" attribute
 * differently from other ItemInfos -- we use it to store information about the split position and
 * ratio.
 */
public class AppPairsController {
    private static final String TAG = "AppPairsController";

    // Used for encoding and decoding the "rank" attribute
    private static final int BITMASK_SIZE = 16;
    private static final int BITMASK_FOR_SNAP_POSITION = (1 << BITMASK_SIZE) - 1;

    private ActivityContext mContext;
    private final SplitSelectStateController mSplitSelectStateController;
    private final StatsLogManager mStatsLogManager;
    public AppPairsController(ActivityContext context,
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
     * Returns whether the specified GroupedTaskView can be saved as an app pair.
     */
    public boolean canSaveAppPair(TaskView taskView) {
        if (mContext == null) {
            // Can ignore as the activity is already destroyed
            return false;
        }

        // Disallow saving app pairs if:
        // - app pairs feature is not enabled
        // - the task in question is a single task
        // - at least one app in app pair is unpinnable
        // - the task is not a GroupedTaskView
        // - both tasks in the GroupedTaskView are from the same app and the app does not
        //   support multi-instance
        boolean hasUnpinnableApp = taskView.getTaskContainers().stream()
                .anyMatch(att -> att != null && att.getItemInfo() != null
                        && ((att.getItemInfo().runtimeStatusFlags
                            & ItemInfoWithIcon.FLAG_NOT_PINNABLE) != 0));
        if (!taskView.containsMultipleTasks()
                || hasUnpinnableApp
                || !(taskView instanceof GroupedTaskView groupedTaskView)) {
            return false;
        }

        ComponentKey leftTopComponentKey = TaskUtils.getLaunchComponentKeyForTask(
                groupedTaskView.getLeftTopTaskContainer().getTask().key);
        ComponentKey rightBottomComponentKey = TaskUtils.getLaunchComponentKeyForTask(
                groupedTaskView.getRightBottomTaskContainer().getTask().key);
        AppInfo leftTopAppInfo = resolveAppInfoByComponent(leftTopComponentKey);
        AppInfo rightBottomAppInfo = resolveAppInfoByComponent(rightBottomComponentKey);

        if (leftTopAppInfo == null || rightBottomAppInfo == null) {
            // Disallow saving app pairs for apps that don't have a front-door in Launcher
            return false;
        }

        if (PackageManagerHelper.isSameAppForMultiInstance(leftTopAppInfo, rightBottomAppInfo)) {
            if (!leftTopAppInfo.supportsMultiInstance()
                    || !rightBottomAppInfo.supportsMultiInstance()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a new app pair ItemInfo and adds it to the workspace.
     * <br>
     * We create WorkspaceItemInfos to save onto the app pair in the following way:
     * <br> 1. We verify that the ComponentKey from our Recents tile corresponds to a real
     * launchable app in the app store.
     * <br> 2. If it doesn't, we search for the underlying launchable app via package name, and use
     * that instead.
     * <br> 3. If that fails, we re-use the existing WorkspaceItemInfo by cloning it and replacing
     * its intent with one from PackageManager.
     * <br> 4. If everything fails, we just use the WorkspaceItemInfo as is, with its existing
     * intent. This is not preferred, but will still work in most cases (notably it will not work
     * well on trampoline apps).
     */
    public void saveAppPair(GroupedTaskView gtv) {
        InteractionJankMonitorWrapper.begin(gtv, Cuj.CUJ_LAUNCHER_SAVE_APP_PAIR);

        @PersistentSnapPosition int snapPosition = gtv.getSnapPosition();
        if (snapPosition == SNAP_TO_NONE) {
            // Free snap mode is enabled, just save it as 50/50 split.
            snapPosition = SNAP_TO_2_50_50;
        }
        if (!isPersistentSnapPosition(snapPosition)) {
            // If we received an illegal snap position, log an error and do not create the app pair
            Log.wtf(TAG, "Tried to save an app pair with illegal snapPosition "
                    + snapPosition);
            return;
        }

        List<TaskViewItemInfo> recentsInfos =
                gtv.getTaskContainers().stream().map(TaskContainer::getItemInfo).toList();
        List<WorkspaceItemInfo> apps =
                recentsInfos.stream().map(this::resolveAppPairWorkspaceInfo).toList();

        if (apps.stream().anyMatch(Objects::isNull)) {
            // This shouldn't happen if canSaveAppPair() is called above, but log an error and do
            // not create the app pair if the workspace items can't be resolved
            StringBuilder error =
                    new StringBuilder("Failed to save app pair due to invalid apps (");
            for (int i = 0; i < recentsInfos.size(); i++) {
                error.append("app").append(i).append("=")
                        .append(recentsInfos.get(i).getComponentKey().componentName).append(" ");
            }
            error.append(")");
            Log.w(TAG, error.toString());
            return;
        }

        for (int i = 0; i < apps.size(); i++) {
            apps.get(i).rank = encodeRank(getIndex(i), snapPosition);
        }
        AppPairInfo newAppPair = new AppPairInfo(apps);

        IconCache iconCache = LauncherAppState.getInstance(mContext.asContext()).getIconCache();
        MODEL_EXECUTOR.execute(() -> {
            newAppPair.getAppContents().forEach(member -> {
                member.title = "";
                member.bitmap = iconCache.getDefaultIcon(newAppPair.user);
                iconCache.getTitleAndIcon(member, member.getMatchingLookupFlag());
            });
            MAIN_EXECUTOR.execute(() -> {
                LauncherAccessibilityDelegate delegate = QuickstepLauncher.getLauncher(
                        mContext.asContext()).getAccessibilityDelegate();
                if (delegate != null) {
                    delegate.addToWorkspace(newAppPair, true, (success) -> {
                        if (success) {
                            InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_SAVE_APP_PAIR);
                        } else {
                            InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_SAVE_APP_PAIR);
                        }
                    });
                    mStatsLogManager.logger().withItemInfo(newAppPair)
                            .log(StatsLogManager.LauncherEvent.LAUNCHER_APP_PAIR_SAVE);
                }
            });
        });
    }

    /**
     * Launches an app pair by searching the RecentsModel for running instances of each app, and
     * staging either those running instances or launching the apps as new Intents.
     *
     * @param cuj Should be an integer from {@link Cuj} or -1 if no CUJ needs to be logged for jank
     *            monitoring
     * @param callback Called after the app pair launch finishes animating, or null if no method is
     *                 to be called
     */
    public void launchAppPair(AppPairIcon appPairIcon, int cuj,
            @Nullable Consumer<Boolean> callback) {
        WorkspaceItemInfo app1 = appPairIcon.getInfo().getFirstApp();
        WorkspaceItemInfo app2 = appPairIcon.getInfo().getSecondApp();
        ComponentKey app1Key = new ComponentKey(app1.getTargetComponent(), app1.user);
        ComponentKey app2Key = new ComponentKey(app2.getTargetComponent(), app2.user);
        mSplitSelectStateController.setLaunchingCuj(appPairIcon, cuj);

        mSplitSelectStateController.findLastActiveTasksAndRunCallback(
                Arrays.asList(app1Key, app2Key),
                false /* findExactPairMatch */,
                foundTasks -> {
                    @Nullable Task foundTask1 = foundTasks[0];
                    Intent task1Intent;
                    int task1Id;
                    if (foundTask1 != null) {
                        task1Id = foundTask1.key.id;
                        task1Intent = null;
                    } else {
                        task1Id = INVALID_TASK_ID;
                        task1Intent = app1.intent;
                    }

                    mSplitSelectStateController.setInitialTaskSelect(task1Intent,
                            AppPairsController.convertRankToStagePosition(app1.rank),
                            app1,
                            LAUNCHER_APP_PAIR_LAUNCH,
                            task1Id);

                    @Nullable Task foundTask2 = foundTasks[1];
                    if (foundTask2 != null) {
                        mSplitSelectStateController.setSecondTask(foundTask2, app2);
                    } else {
                        mSplitSelectStateController.setSecondTask(
                                app2.intent, app2.user, app2);
                    }

                    mSplitSelectStateController.setLaunchingIconView(appPairIcon);

                    mSplitSelectStateController.launchSplitTasks(
                            AppPairsController.convertRankToSnapPosition(app1.rank), callback);
                }
        );
    }

    /**
     * Launches an app pair but does not specify a callback
     */
    public void launchAppPair(AppPairIcon appPairIcon, int cuj) {
        launchAppPair(appPairIcon, cuj, null);
    }

    /**
     * Returns an AppInfo associated with the app for the given ComponentKey, or null if no such
     * package exists in the AllAppsStore.
     */
    @Nullable
    private AppInfo resolveAppInfoByComponent(@NonNull ComponentKey key) {
        AllAppsStore appsStore = ActivityContext.lookupContext(mContext.asContext())
                .getAppsView().getAppsStore();

        // First look up the app info in order of:
        // - The exact activity for the recent task
        // - The first(?) loaded activity from the package
        AppInfo appInfo = appsStore.getApp(key);
        if (appInfo == null) {
            appInfo = appsStore.getApp(key, PACKAGE_KEY_COMPARATOR);
        }
        return appInfo;
    }

    /**
     * Creates a new launchable WorkspaceItemInfo of itemType=ITEM_TYPE_APPLICATION by looking the
     * ComponentKey up in the AllAppsStore. If no app is found, attempts a lookup by package
     * instead. If that lookup fails, returns null.
     */
    @Nullable
    private WorkspaceItemInfo resolveAppPairWorkspaceInfo(
            @NonNull WorkspaceItemInfo recentTaskInfo) {
        // ComponentKey should never be null (see TaskView#getItemInfo)
        AppInfo appInfo = resolveAppInfoByComponent(recentTaskInfo.getComponentKey());
        if (appInfo == null) {
            return null;
        }
        return appInfo.makeWorkspaceItem(mContext.asContext());
    }

    /**
     * Handles the complicated logic for how to animate an app pair entrance when already inside an
     * app or app pair.
     *
     * If the user tapped on an app pair while already in an app pair, there are 4 general cases:
     *   a) Clicked app pair A|B, but both apps are already running on screen.
     *   b) App A is already on-screen, but App B isn't.
     *   c) App B is on-screen, but App A isn't.
     *   d) Neither is on-screen.
     *
     * If the user tapped an app pair while a fullscreen or freeform app is visible on screen,
     * there are 4 cases:
     *   a) At least one of the apps in the app pair is in freeform windowing mode.
     *   b) The on-screen app is App A of the app pair.
     *   c) The on-screen app is App B of the app pair.
     *   d) It is neither.
     *
     * For each case, we call the appropriate animation and split launch type.
     */
    public void handleAppPairLaunchInApp(AppPairIcon launchingIconView,
            List<? extends ItemInfo> itemInfos) {
        TaskbarActivityContext context = (TaskbarActivityContext) launchingIconView.getContext();
        List<ComponentKey> componentKeys =
                itemInfos.stream().map(ItemInfo::getComponentKey).toList();

        // Use TopTaskTracker to find the currently running app (or apps)
        TopTaskTracker topTaskTracker = getTopTaskTracker();

        // getRunningSplitTasksIds() will return a pair of ids if we are currently running a
        // split pair, or an empty array with zero length if we are running a single app.
        int[] runningSplitTasks = topTaskTracker.getRunningSplitTaskIds();
        if (runningSplitTasks != null && runningSplitTasks.length == 2) {
            // Tapped an app pair while in an app pair
            int runningTaskId1 = runningSplitTasks[0];
            int runningTaskId2 = runningSplitTasks[1];

            mSplitSelectStateController.findLastActiveTasksAndRunCallback(
                    componentKeys,
                    false /* findExactPairMatch */,
                    foundTasks -> {
                        // If our clicked app pair has already-running Tasks, we grab the
                        // taskIds here so we can see if those ids are already on-screen now
                        List<Integer> lastActiveTasksOfAppPair =
                                Arrays.stream(foundTasks).map((Task task) -> {
                                    if (task != null) {
                                        return task.getKey().getId();
                                    } else {
                                        return INVALID_TASK_ID;
                                    }
                                }).toList();

                        if (lastActiveTasksOfAppPair.contains(runningTaskId1)
                                && lastActiveTasksOfAppPair.contains(runningTaskId2)) {
                            // App A and App B are already on-screen, so do nothing.
                        } else if (!lastActiveTasksOfAppPair.contains(runningTaskId1)
                                && !lastActiveTasksOfAppPair.contains(runningTaskId2)) {
                            // Neither A nor B are on screen, so just launch a new app pair
                            // normally.
                            launchAppPair(launchingIconView,
                                    CUJ_LAUNCHER_LAUNCH_APP_PAIR_FROM_TASKBAR);
                        } else {
                            // Exactly one app (A or B) is on-screen, so we have to launch the other
                            // on the appropriate side.
                            ItemInfo app1 = itemInfos.get(0);
                            ItemInfo app2 = itemInfos.get(1);
                            int task1 = lastActiveTasksOfAppPair.get(0);
                            int task2 = lastActiveTasksOfAppPair.get(1);

                            // If task1 is one of the running on-screen tasks, we launch app2.
                            // If not, task2 must be the running task, and we launch app1.
                            ItemInfo appToLaunch =
                                    task1 == runningTaskId1 || task1 == runningTaskId2
                                            ? app2
                                            : app1;
                            // If the on-screen task is on the bottom/right position, we launch to
                            // the top/left. If not, we launch to the bottom/right.
                            @StagePosition int sideToLaunch =
                                    task1 == runningTaskId2 || task2 == runningTaskId2
                                            ? STAGE_POSITION_TOP_OR_LEFT
                                            : STAGE_POSITION_BOTTOM_OR_RIGHT;

                            launchToSide(context, launchingIconView.getInfo(), appToLaunch,
                                    sideToLaunch);
                        }
                    }
            );
        } else {
            // Tapped an app pair while in a single app
            final TopTaskTracker.CachedTaskInfo runningTask = topTaskTracker
                    .getCachedTopTask(false /* filterOnlyVisibleRecents */, context.getDisplayId());

            mSplitSelectStateController.findLastActiveTasksAndRunCallback(
                    componentKeys,
                    false /* findExactPairMatch */,
                    foundTasks -> {
                        Task foundTask1 = foundTasks[0];
                        Task foundTask2 = foundTasks[1];

                        if (DesktopModeStatus.canEnterDesktopMode(context) && (isFreeformTask(
                                foundTask1) || isFreeformTask(foundTask2))) {
                            launchAppPair(launchingIconView,
                                    CUJ_LAUNCHER_LAUNCH_APP_PAIR_FROM_TASKBAR);
                            return;
                        }

                        boolean task1IsOnScreen;
                        boolean task2IsOnScreen;
                        if (com.android.wm.shell.Flags.enableShellTopTaskTracking()) {
                            task1IsOnScreen = foundTask1 != null
                                    && runningTask.topGroupedTaskContainsTask(
                                    foundTask1.getKey().getId());
                            task2IsOnScreen = foundTask2 != null
                                    && runningTask.topGroupedTaskContainsTask(
                                    foundTask2.getKey().getId());
                        } else {
                            task1IsOnScreen = foundTask1 != null && foundTask1.getKey().getId()
                                    == runningTask.getTaskId();
                            task2IsOnScreen = foundTask2 != null && foundTask2.getKey().getId()
                                    == runningTask.getTaskId();
                        }

                        if (!task1IsOnScreen && !task2IsOnScreen) {
                            // Neither App A nor App B are on-screen, launch the app pair normally.
                            launchAppPair(launchingIconView,
                                    CUJ_LAUNCHER_LAUNCH_APP_PAIR_FROM_TASKBAR);
                        } else {
                            // Either A or B is on-screen, so launch the other on the appropriate
                            // side.
                            ItemInfo app1 = itemInfos.get(0);
                            ItemInfo app2 = itemInfos.get(1);
                            // If task1 is the running on-screen task, we launch app2 on the
                            // bottom/right. If task2 is on-screen, launch app1 on the top/left.
                            ItemInfo appToLaunch = task1IsOnScreen ? app2 : app1;
                            @StagePosition int sideToLaunch = task1IsOnScreen
                                    ? STAGE_POSITION_BOTTOM_OR_RIGHT
                                    : STAGE_POSITION_TOP_OR_LEFT;

                            launchToSide(context, launchingIconView.getInfo(), appToLaunch,
                                    sideToLaunch);
                        }
                }
            );
        }
    }

    /**
     * Executes a split launch by launching an app to the side of an existing app.
     * @param context The TaskbarActivityContext that we are launching the app pair from.
     * @param launchingItemInfo The itemInfo of the icon that was tapped.
     * @param app The app that will launch to the side of the existing running app (not necessarily
     *  the same as the previous parameter; e.g. we tap an app pair but launch an app).
     * @param side A @StagePosition, either STAGE_POSITION_TOP_OR_LEFT or
     *  STAGE_POSITION_BOTTOM_OR_RIGHT.
     */
    @VisibleForTesting
    public void launchToSide(
            TaskbarActivityContext context,
            ItemInfo launchingItemInfo,
            ItemInfo app,
            @StagePosition int side
    ) {
        LauncherApps launcherApps = context.getSystemService(LauncherApps.class);

        // Set up to log app pair launch event
        Pair<com.android.internal.logging.InstanceId, InstanceId> instanceIds =
                LogUtils.getShellShareableInstanceId();
        context.getStatsLogManager()
                .logger()
                .withItemInfo(launchingItemInfo)
                .withInstanceId(instanceIds.second)
                .log(LAUNCHER_APP_PAIR_LAUNCH);

        SystemUiProxy.INSTANCE.get(context)
                .startIntent(
                        launcherApps.getMainActivityLaunchIntent(
                                app.getIntent().getComponent(),
                                null,
                                app.user
                        ),
                        app.user.getIdentifier(),
                        new Intent(),
                        side,
                        null,
                        instanceIds.first
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
     * Gets the TopTaskTracker, which is a cached record of the top running Task.
     */
    @VisibleForTesting
    public TopTaskTracker getTopTaskTracker() {
        return TopTaskTracker.INSTANCE.get(mContext.asContext());
    }
}
