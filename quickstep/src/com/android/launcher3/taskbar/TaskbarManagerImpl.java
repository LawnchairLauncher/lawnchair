/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.content.pm.PackageManager.FEATURE_PC;
import static android.os.Process.THREAD_PRIORITY_FOREGROUND;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.launcher3.BaseActivity.EVENT_DESTROYED;
import static com.android.launcher3.Flags.enableGrowthNudge;
import static com.android.launcher3.Flags.enableTaskbarUiThread;
import static com.android.launcher3.Flags.enableUnfoldStateAnimation;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarNoRecreate;
import static com.android.launcher3.taskbar.growth.GrowthConstants.BROADCAST_SHOW_NUDGE;
import static com.android.launcher3.taskbar.growth.GrowthConstants.GROWTH_NUDGE_PERMISSION;
import static com.android.launcher3.util.DisplayController.CHANGE_DENSITY;
import static com.android.launcher3.util.DisplayController.CHANGE_DESKTOP_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_SHOW_LOCKED_TASKBAR;
import static com.android.launcher3.util.DisplayController.CHANGE_TASKBAR_PINNING;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.FlagDebugUtils.formatFlagChange;
import static com.android.quickstep.util.SystemActionConstants.ACTION_SHOW_TASKBAR;
import static com.android.quickstep.util.SystemActionConstants.SYSTEM_ACTION_ID_TASKBAR;
import static com.android.wm.shell.shared.desktopmode.DesktopModeStatus.enableMultipleDesktops;

import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Trace;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.DesktopExperienceFlags;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.app.displaylib.DisplayDecorationListener;
import com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat;
import com.android.app.displaylib.PerDisplayRepository;
import com.android.internal.util.LatencyTracker;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks;
import com.android.launcher3.taskbar.unfold.NonDestroyableScopedUnfoldTransitionProgressProvider;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.AllAppsActionManager;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.SystemDecorationChangeObserver;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.util.ContextualSearchInvoker;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.views.RecentsViewContainer;
//import com.android.server.am.Flags;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

import kotlinx.coroutines.CoroutineDispatcher;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;

import app.lawnchair.LawnchairApp;

/**
 * Class to manage taskbar lifecycle
 */
public class TaskbarManagerImpl implements DisplayDecorationListener {
    private static final String TAG = "TaskbarManager";
    private static final boolean DEBUG = false;
    private static final int TASKBAR_DESTROY_DURATION = 100;

    // TODO: b/397738606  - Remove all logs with this tag after the growth framework is integrated.
    public static final String GROWTH_FRAMEWORK_TAG = "Growth Framework";

    /**
     * All the configurations which do not initiate taskbar recreation.
     * This includes all the configurations defined in Launcher's manifest entry and
     * ActivityController#filterConfigChanges
     */
    private static final int SKIP_RECREATE_CONFIG_CHANGES = ActivityInfo.CONFIG_WINDOW_CONFIGURATION
            | ActivityInfo.CONFIG_KEYBOARD
            | ActivityInfo.CONFIG_KEYBOARD_HIDDEN
            | ActivityInfo.CONFIG_MCC
            | ActivityInfo.CONFIG_MNC
            | ActivityInfo.CONFIG_NAVIGATION
            | ActivityInfo.CONFIG_ORIENTATION
            | ActivityInfo.CONFIG_SCREEN_SIZE
            | ActivityInfo.CONFIG_SCREEN_LAYOUT
            | ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE;

    private static final Uri USER_SETUP_COMPLETE_URI = Settings.Secure.getUriFor(
            Settings.Secure.USER_SETUP_COMPLETE);

    private static final Uri NAV_BAR_KIDS_MODE = Settings.Secure.getUriFor(
            Settings.Secure.NAV_BAR_KIDS_MODE);

    private static final LooperExecutor TASKBAR_UI_THREAD =
            new LooperExecutor("TASKBAR_UI_THREAD", THREAD_PRIORITY_FOREGROUND);

    private final Context mBaseContext;
    private final int mPrimaryDisplayId;
    private final TaskbarNavButtonCallbacks mNavCallbacks;
    // TODO: Remove this during the connected displays lifecycle refactor.
    private final Context mPrimaryWindowContext;
    private final WindowManager mPrimaryWindowManager;
    private final DisplayManager mDisplayManager;
    private TaskbarNavButtonController mPrimaryNavButtonController;
    private ComponentCallbacks mPrimaryComponentCallbacks;

    private final SimpleBroadcastReceiver mShutdownReceiver;
    private final DisplaysWithDecorationsRepositoryCompat mDisplaysWithDecorationsRepositoryCompat;

    // The source for this provider is set when Launcher is available
    // We use 'non-destroyable' version here so the original provider won't be destroyed
    // as it is tied to the activity lifecycle, not the taskbar lifecycle.
    // It's destruction/creation will be managed by the activity.
    private final ScopedUnfoldTransitionProgressProvider mUnfoldProgressProvider =
            new NonDestroyableScopedUnfoldTransitionProgressProvider();
    /** DisplayId - {@link TaskbarActivityContext} map for Connected Display. */
    private final Map<Integer, TaskbarActivityContext>
            mTaskbars = enableTaskbarUiThread() ? new ConcurrentHashMap<>() : new HashMap<>();
    /** DisplayId - {@link Context} map for Connected Display. */
    private final SparseArray<Context> mWindowContexts = new SparseArray<>();
    /** DisplayId - {@link FrameLayout} map for Connected Display. */
    private final SparseArray<FrameLayout> mRootLayouts = new SparseArray<>();
    /** DisplayId - {@link Boolean} map indicating if RootLayout was added to window. */
    private final SparseBooleanArray mAddedRootLayouts = new SparseBooleanArray();
    /** DisplayId - {@link TaskbarNavButtonController} map for Connected Display. */
    private final SparseArray<TaskbarNavButtonController> mNavButtonControllers =
            new SparseArray<>();
    /** DisplayId - {@link ComponentCallbacks} map for Connected Display. */
    private final SparseArray<ComponentCallbacks> mComponentCallbacks = new SparseArray<>();
    /** DisplayId - {@link deviceprofile} map for Connected Display. */
    private final SparseArray<DeviceProfile> mExternalDeviceProfiles = new SparseArray<>();
    private StatefulActivity mActivity;
    private RecentsViewContainer mRecentsViewContainer;
    /** Whether this device is a desktop android device **/
    private boolean mIsAndroidPC;
    /** Whether this device supports freeform windows management. Can change dynamically **/
    private boolean mSupportsFreeformWindowsManagement;

    /**
     * Cache a copy here so we can initialize state whenever taskbar is recreated, since
     * this class does not get re-initialized w/ new taskbars.
     */
    private final Map<Integer, TaskbarSharedState> mTaskbarSharedStates = new ConcurrentHashMap<>();

    /**
     * We use WindowManager's ComponentCallbacks() for internal UI changes (similar to an Activity)
     * which comes via a different channel
     */
    private final RecreationListener mRecreationListener = new RecreationListener();

    // Currently, there is a duplicative call to recreate taskbars when user enter/exit Desktop
    // Mode upon getting transition callback from shell side. So, we make sure that if taskbar is
    // already in recreate process due to transition callback, don't recreate for
    // DisplayInfoChangeListener.
    private boolean mShouldIgnoreNextDesktopModeChangeFromDisplayControllerForPrimary = false;

    private class RecreationListener implements DisplayController.DisplayInfoChangeListener {
        @Override
        public void onDisplayInfoChanged(Context context, DisplayController.Info info, int flags) {
            int displayId = context.getDisplayId();
            if ((flags & CHANGE_DENSITY) != 0) {
                debugTaskbarManager("onDisplayInfoChanged: Display density changed", displayId);
            }
            if ((flags & CHANGE_NAVIGATION_MODE) != 0) {
                debugTaskbarManager("onDisplayInfoChanged: Navigation mode changed", displayId);
            }
            if ((flags & CHANGE_DESKTOP_MODE) != 0) {
                debugTaskbarManager("onDisplayInfoChanged: Desktop mode changed", displayId);
                handleDisplayUpdatesForPerceptibleTasks();
            }
            if ((flags & CHANGE_TASKBAR_PINNING) != 0) {
                debugTaskbarManager("onDisplayInfoChanged: Taskbar pinning changed", displayId);
            }

            // Use a helper to update DP (only for secondary displays) and then recreate taskbar.
            IntConsumer updateExternalDpAndRecreateTaskbar = displayIdToUpdate -> {
                // Don't update DP for primary display as IDP already takes care of this.
                createExternalDeviceProfile(displayIdToUpdate);
                recreateTaskbarForDisplay(displayIdToUpdate, /* duration= */ 0);
            };

            if ((flags & (CHANGE_DENSITY | CHANGE_NAVIGATION_MODE | CHANGE_DESKTOP_MODE
                    | CHANGE_TASKBAR_PINNING | CHANGE_SHOW_LOCKED_TASKBAR)) != 0) {

                TaskbarActivityContext taskbarActivityContext = getTaskbarForDisplay(displayId);
                if ((flags & CHANGE_SHOW_LOCKED_TASKBAR) != 0) {
                    debugTaskbarManager("onDisplayInfoChanged: show locked taskbar changed!",
                            displayId);
                    updateExternalDpAndRecreateTaskbar.accept(displayId);
                } else if ((flags & CHANGE_DESKTOP_MODE) != 0) {
                    if (displayId == mPrimaryDisplayId
                            && mShouldIgnoreNextDesktopModeChangeFromDisplayControllerForPrimary) {
                        mShouldIgnoreNextDesktopModeChangeFromDisplayControllerForPrimary = false;
                        return;
                    }
                    // Only Handles Special Exit Cases for Desktop Mode Taskbar Recreation.
                    if (((flags & CHANGE_TASKBAR_PINNING) != 0) || (taskbarActivityContext != null
                            && !taskbarActivityContext.showLockedTaskbarOnHome()
                            && !taskbarActivityContext.showDesktopTaskbarForFreeformDisplay())) {
                        updateExternalDpAndRecreateTaskbar.accept(displayId);
                    }
                } else {
                    updateExternalDpAndRecreateTaskbar.accept(displayId);
                }
            }
        }
    }

    private final SettingsCache.OnChangeListener mOnSettingsChangeListener = c -> {
        debugPrimaryTaskbar("Settings changed! Recreating Taskbar!");
        recreateTaskbars();
    };

    private PerceptibleTaskListener mTaskStackListener;

    private class PerceptibleTaskListener implements TaskStackChangeListener {
        private ArraySet<Integer> mPerceptibleTasks = new ArraySet<Integer>();

        @Override
        public void onTaskMovedToFront(int taskId) {
            // This listens to any Task, so we filter them by the ones shown in the launcher.
            // For Tasks restored after startup, they will by default not be Perceptible, and no
            // need to until user interacts with it by bringing it to the foreground.
            for (Entry<Integer, TaskbarActivityContext> entry : mTaskbars.entrySet()) {
                // get pinned tasks - we care about all tasks, not just the one moved to the front
                Set<Integer> taskbarPinnedTasks =
                        entry.getValue().getControllers().taskbarViewController
                                .getShownTaskIds();

                // filter out tasks already marked as perceptible
                taskbarPinnedTasks.removeAll(mPerceptibleTasks);

                // add the filtered tasks as perceptible
                for (int pinnedTaskId : taskbarPinnedTasks) {
                    ActivityManagerWrapper.getInstance()
                            .setTaskIsPerceptible(pinnedTaskId, true);
                    mPerceptibleTasks.add(pinnedTaskId);
                }
            }
        }

        /**
         * Launcher also can display recently launched tasks that are not pinned. Also add
         * these as perceptible
         */
        @Override
        public void onRecentTaskListUpdated() {
            for (Entry<Integer, TaskbarActivityContext> entry : mTaskbars.entrySet()) {
                for (GroupTask gTask : entry.getValue().getControllers()
                        .taskbarRecentAppsController.getShownTasks()) {
                    for (Task task : gTask.getTasks()) {
                        int taskId = task.key.id;

                        if (!mPerceptibleTasks.contains(taskId)) {
                            ActivityManagerWrapper.getInstance()
                                    .setTaskIsPerceptible(taskId, true);
                            mPerceptibleTasks.add(taskId);
                        }
                    }
                }
            }
        }

        @Override
        public void onTaskRemoved(int taskId) {
            mPerceptibleTasks.remove(taskId);
        }

        public void unregisterListener() {
            for (Integer taskId : mPerceptibleTasks) {
                ActivityManagerWrapper.getInstance().setTaskIsPerceptible(taskId, false);
            }
            TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                    mTaskStackListener);
        }
    }

    private final DesktopVisibilityController.TaskbarDesktopModeListener
            mTaskbarDesktopModeListener =
            new DesktopVisibilityController.TaskbarDesktopModeListener() {
                @Override
                public void onExitDesktopMode(int duration) {
                    if (enableMultipleDesktops(mBaseContext)) {
                        LatencyTracker.getInstance(mBaseContext).onActionStart(
                                LatencyTracker.ACTION_DESKTOP_MODE_EXIT_MODE_ON_LAST_WINDOW_CLOSE);
                    }

                    TaskbarActivityContext taskbarActivityContext = getCurrentActivityContext();
                    if (taskbarActivityContext != null
                            && !taskbarActivityContext.isInOverview()
                            && !taskbarActivityContext.showDesktopTaskbarForFreeformDisplay()) {
                        mShouldIgnoreNextDesktopModeChangeFromDisplayControllerForPrimary = true;
                        AnimatorSet animatorSet = taskbarActivityContext.onDestroyAnimation(
                                TASKBAR_DESTROY_DURATION);
                        animatorSet.addListener(AnimatorListeners.forEndCallback(
                                () -> recreateTaskbarForDisplay(mPrimaryDisplayId, duration)));
                        animatorSet.start();
                    }
                }

                @Override
                public void onEnterDesktopMode(int duration) {
                    TaskbarActivityContext taskbarActivityContext = getCurrentActivityContext();
                    if (taskbarActivityContext != null
                            && !taskbarActivityContext.showDesktopTaskbarForFreeformDisplay()) {
                        mShouldIgnoreNextDesktopModeChangeFromDisplayControllerForPrimary = true;
                        AnimatorSet animatorSet = taskbarActivityContext.onDestroyAnimation(
                                TASKBAR_DESTROY_DURATION);
                        animatorSet.addListener(AnimatorListeners.forEndCallback(
                                () -> recreateTaskbarForDisplay(mPrimaryDisplayId, duration)));
                        animatorSet.start();
                    }
                }

                @Override
                public void onTaskbarCornerRoundingUpdate(
                        boolean doesAnyTaskRequireTaskbarRounding) {
                    //NO-OP
                }
            };

    private boolean mUserUnlocked = false;

    private final Map<Integer, SimpleBroadcastReceiver> mTaskbarBroadcastReceivers =
            new ConcurrentHashMap<>();

    private final SimpleBroadcastReceiver mGrowthBroadcastReceiver;

    private final AllAppsActionManager mAllAppsActionManager;
    private final PerDisplayRepository<RecentsWindowManager> mRecentsWindowManagerRepository;

    private final Runnable mActivityOnDestroyCallback = new Runnable() {
        @Override
        public void run() {
            int displayId = mPrimaryDisplayId;
            debugTaskbarManager("onActivityDestroyed:", displayId);
            if (mActivity != null) {
                displayId = mActivity.getDisplayId();
                mActivity.removeOnDeviceProfileChangeListener(
                        mDebugActivityDeviceProfileChanged);
                debugTaskbarManager("onActivityDestroyed: unregistering callbacks", displayId);
                mActivity.removeEventCallback(EVENT_DESTROYED, this);
            }
            if (mActivity == mRecentsViewContainer) {
                mRecentsViewContainer = null;
            }
            mActivity = null;
            TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
            if (taskbar != null) {
                debugTaskbarManager("onActivityDestroyed: setting taskbarUIController", displayId);
                taskbar.setUIController(TaskbarUIController.DEFAULT);
            } else {
                debugTaskbarManager("onActivityDestroyed: taskbar is null!", displayId);
            }
            mUnfoldProgressProvider.setSourceProvider(null);
        }
    };

    UnfoldTransitionProgressProvider.TransitionProgressListener mUnfoldTransitionProgressListener =
            new UnfoldTransitionProgressProvider.TransitionProgressListener() {
                @Override
                public void onTransitionStarted() {
                    debugPrimaryTaskbar("fold/unfold transition started getting called.");
                }

                @Override
                public void onTransitionProgress(float progress) {
                    debugPrimaryTaskbar(
                            "fold/unfold transition progress getting called. | progress="
                                    + progress);
                }

                @Override
                public void onTransitionFinishing() {
                    debugPrimaryTaskbar(
                            "fold/unfold transition finishing getting called.");

                }

                @Override
                public void onTransitionFinished() {
                    debugPrimaryTaskbar(
                            "fold/unfold transition finished getting called.");
                }
            };

    @SuppressLint("WrongConstant")
    public TaskbarManagerImpl(
            Context context,
            AllAppsActionManager allAppsActionManager,
            TaskbarNavButtonCallbacks navCallbacks,
            PerDisplayRepository<RecentsWindowManager> recentsWindowManagerRepository,
            DisplaysWithDecorationsRepositoryCompat displaysWithDecorationsRepositoryCompat,
            CoroutineDispatcher dispatcher) {
        mBaseContext = context;
        mPrimaryDisplayId = mBaseContext.getDisplayId();
        mAllAppsActionManager = allAppsActionManager;
        mNavCallbacks = navCallbacks;
        mRecentsWindowManagerRepository = recentsWindowManagerRepository;
        mDisplaysWithDecorationsRepositoryCompat = displaysWithDecorationsRepositoryCompat;

        // Set up primary display.
        debugPrimaryTaskbar("TaskbarManager constructor");
        mDisplayManager = mBaseContext.getSystemService(DisplayManager.class);
        mPrimaryWindowContext = createWindowContext(mPrimaryDisplayId);
        addWindowContextToMap(mPrimaryDisplayId, mPrimaryWindowContext);
        mPrimaryWindowManager = mPrimaryWindowContext.getSystemService(WindowManager.class);
        DesktopVisibilityController.INSTANCE.get(
                mPrimaryWindowContext).registerTaskbarDesktopModeListener(
                mTaskbarDesktopModeListener);
        createTaskbarRootLayout(mPrimaryDisplayId);
        createNavButtonController(mPrimaryDisplayId);
        createAndRegisterComponentCallbacks(mPrimaryDisplayId);

        SettingsCache.INSTANCE.get(mPrimaryWindowContext)
                .register(USER_SETUP_COMPLETE_URI, mOnSettingsChangeListener);
        SettingsCache.INSTANCE.get(mPrimaryWindowContext)
                .register(NAV_BAR_KIDS_MODE, mOnSettingsChangeListener);
        if (DesktopExperienceFlags.ENABLE_SYS_DECORS_CALLBACKS_VIA_WM.isTrue()
                && DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue()) {
            displaysWithDecorationsRepositoryCompat
                    .registerDisplayDecorationListener(this, dispatcher);
        } else {
            SystemDecorationChangeObserver.getINSTANCE().get(mPrimaryWindowContext)
                    .registerDisplayDecorationListener(this);
            addSystemDecorationForDisplaysAtBoot();
        }
        mShutdownReceiver =
                new SimpleBroadcastReceiver(
                        mPrimaryWindowContext, UI_HELPER_EXECUTOR, i -> destroyAllTaskbars());

        mShutdownReceiver.register(Intent.ACTION_SHUTDOWN);
        if (enableGrowthNudge()) {
            // TODO: b/397739323 - Add permission to limit access to Growth Framework.
            mGrowthBroadcastReceiver =
                    new SimpleBroadcastReceiver(
                            mPrimaryWindowContext, UI_HELPER_EXECUTOR, this::showGrowthNudge);
            mGrowthBroadcastReceiver.register(null, GROWTH_NUDGE_PERMISSION, RECEIVER_EXPORTED,
                    BROADCAST_SHOW_NUDGE);
        } else {
            mGrowthBroadcastReceiver = null;
        }

        mIsAndroidPC = getPrimaryWindowContext().getPackageManager().hasSystemFeature(FEATURE_PC);
        mSupportsFreeformWindowsManagement = getFreeformWindowsManagementInfo();

        if (eligibleForPerceptibleTasks()) {
            mTaskStackListener = new PerceptibleTaskListener();
            TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
        } else {
            mTaskStackListener = null;
        }
        recreateTaskbarForDisplay(mPrimaryDisplayId, /* duration= */ 0);

        debugPrimaryTaskbar("TaskbarManager created");
    }

    /**
     * Calls {@link #onDisplayAddSystemDecorations(int)} for all displays
     * TODO b/408503553: Remove when WM is used instead of CommandQueue for system decorations.
     */
    private void addSystemDecorationForDisplaysAtBoot() {
        if (mDisplayManager == null) {
            return;
        }

        for (Display display : mDisplayManager.getDisplays()) {
            onDisplayAddSystemDecorations(display.getDisplayId());
        }
    }

    public LooperExecutor getPerWindowUiExecutor() {
        return TASKBAR_UI_THREAD;
    }

    private void handleDisplayUpdatesForPerceptibleTasks() {
        // 1. When desktop mode changes, detect eligibility for perceptible tasks.
        // 2. When no longer eligible for perceptible tasks, turn off and clean up.
        mSupportsFreeformWindowsManagement = getFreeformWindowsManagementInfo();
        if (eligibleForPerceptibleTasks()) {
            if (mTaskStackListener == null) {
                mTaskStackListener = new PerceptibleTaskListener();
                TaskStackChangeListeners.getInstance()
                        .registerTaskStackListener(mTaskStackListener);
            }
        } else {
            // not eligible for perceptible tasks, so we should unregister the listener
            if (mTaskStackListener != null) {
                mTaskStackListener.unregisterListener();
                mTaskStackListener = null;
            }
        }
    }

    private boolean getFreeformWindowsManagementInfo() {
        return getPrimaryWindowContext().getPackageManager().hasSystemFeature(
                FEATURE_FREEFORM_WINDOW_MANAGEMENT);
    }

    private void destroyAllTaskbars() {
        debugPrimaryTaskbar("destroyAllTaskbars");
        for (Entry<Integer, TaskbarActivityContext> entry : new ArraySet<>(mTaskbars.entrySet())) {
            int displayId = entry.getKey();
            debugTaskbarManager("destroyAllTaskbars: call destroyTaskbarForDisplay", displayId);
            destroyTaskbarForDisplay(entry.getValue());

            debugTaskbarManager("destroyAllTaskbars: call removeTaskbarRootViewFromWindow",
                    displayId);
            removeTaskbarRootViewFromWindow(displayId);
        }
    }

    private void destroyTaskbarForDisplay(int displayId) {
        TaskbarActivityContext taskbar = mTaskbars.get(displayId);
        if (taskbar == null) {
            debugTaskbarManager("destroyTaskbarForDisplay: taskbar is NULL!", displayId);
            return;
        }
        destroyTaskbarForDisplay(taskbar);
    }

    private void destroyTaskbarForDisplay(TaskbarActivityContext taskbar) {
        final int displayId = taskbar.getDisplayId();
        debugTaskbarManager("destroyTaskbarForDisplay", displayId);
        taskbar.onDestroy();
        // remove all defaults that we store
        removeTaskbarFromMap(displayId);

        DeviceProfile dp = getDeviceProfile(displayId);
        if (dp == null || !isTaskbarEnabled(dp)) {
            removeTaskbarRootViewFromWindow(displayId);
        }
    }

    /**
     * Show Taskbar upon receiving broadcast
     */
    private void showTaskbarFromBroadcast(Intent intent, int displayId) {
        debugTaskbarManager("destroyTaskbarForDisplay", displayId);
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (ACTION_SHOW_TASKBAR.equals(intent.getAction()) && taskbar != null) {
            taskbar.showTaskbarFromBroadcast();
        }
    }

    private void showGrowthNudge(Intent intent) {
        if (!enableGrowthNudge()) {
            return;
        }
        if (BROADCAST_SHOW_NUDGE.equals(intent.getAction())) {
            // TODO: b/397738606 - extract the details and create a nudge payload.
            Log.d(GROWTH_FRAMEWORK_TAG, "Intent received");
        }
    }

    /**
     * Shows or hides the All Apps view in the Taskbar or Launcher, based on its current
     * visibility on the System UI tracked focused display.
     */
    private void toggleAllAppsSearch() {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getFocusedDisplayId());
        if (taskbar == null) {
            // Home All Apps should be toggled from this class, because the controllers are not
            // initialized when Taskbar is disabled (i.e. TaskbarActivityContext is null).
            if (mActivity instanceof Launcher l) l.toggleAllApps(true);
        } else {
            taskbar.getControllers().uiController.toggleAllApps(true);
        }
    }

    /**
     * Displays a frame of the first Launcher reveal animation.
     *
     * This should be used to run a first Launcher reveal animation whose progress matches a swipe
     * progress.
     */
    public AnimatorPlaybackController createLauncherStartFromSuwAnim(int duration) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(mPrimaryDisplayId);
        return taskbar == null ? null : taskbar.createLauncherStartFromSuwAnim(duration);
    }

    /** Called when the user is unlocked */
    public void onUserUnlocked() {
        debugPrimaryTaskbar("onUserUnlocked");
        mUserUnlocked = true;
        addRecreationListener(mPrimaryDisplayId);
        debugPrimaryTaskbar("onUserUnlocked: recreating all taskbars!");
        // Create DPs for all connected displays if required.
        for (int i = 0; i < mWindowContexts.size(); i++) {
            int displayId = mWindowContexts.keyAt(i);
            if (displayId != mPrimaryDisplayId && !mExternalDeviceProfiles.contains(displayId)) {
                createExternalDeviceProfile(displayId);
                addRecreationListener(displayId);
            }
        }

        recreateTaskbars();
        for (Entry<Integer, TaskbarActivityContext> entry : mTaskbars.entrySet()) {
            int displayId = entry.getKey();
            debugTaskbarManager("onUserUnlocked: addTaskbarRootViewToWindow()", displayId);
            addTaskbarRootViewToWindow(entry.getValue());
        }
    }

    /**
     * Sets a {@link StatefulActivity} to act as taskbar callback
     */
    public void setActivity(@NonNull StatefulActivity activity) {
        debugPrimaryTaskbar("setActivity: mActivity=" + mActivity);
        if (mActivity == activity) {
            debugPrimaryTaskbar("setActivity: No need to set activity!");
            return;
        }
        removeActivityCallbacksAndListeners();
        mActivity = activity;
        mActivity.addOnDeviceProfileChangeListener(mDebugActivityDeviceProfileChanged);
        debugPrimaryTaskbar("setActivity: registering activity lifecycle callbacks.");
        mActivity.addEventCallback(EVENT_DESTROYED, mActivityOnDestroyCallback);
        UnfoldTransitionProgressProvider unfoldTransitionProgressProvider =
                getUnfoldTransitionProgressProviderForActivity(activity);
        if (unfoldTransitionProgressProvider != null) {
            unfoldTransitionProgressProvider.addCallback(mUnfoldTransitionProgressListener);
        }
        mUnfoldProgressProvider.setSourceProvider(unfoldTransitionProgressProvider);

        if (activity instanceof RecentsViewContainer recentsViewContainer) {
            setRecentsViewContainer(recentsViewContainer);
        }
    }

    /**
     * Sets the current RecentsViewContainer, from which we create a TaskbarUIController.
     */
    public void setRecentsViewContainer(@NonNull RecentsViewContainer recentsViewContainer) {
        debugPrimaryTaskbar("setRecentsViewContainer");
        if (mRecentsViewContainer == recentsViewContainer) {
            return;
        }
        if (mRecentsViewContainer == mActivity) {
            // When switching to RecentsWindowManager (not an Activity), the old mActivity is not
            // destroyed, nor is there a new Activity to replace it. Thus if we don't clear it here,
            // it will not get re-set properly if we return to the Activity (e.g. NexusLauncher).
            mActivityOnDestroyCallback.run();
        }
        mRecentsViewContainer = recentsViewContainer;
        TaskbarActivityContext taskbar = getCurrentActivityContext();
        if (taskbar != null) {
            taskbar.setUIController(
                    createTaskbarUIControllerForRecentsViewContainer(mRecentsViewContainer,
                            mPrimaryDisplayId));
        }
    }

    /**
     * Returns an {@link UnfoldTransitionProgressProvider} to use while the given StatefulActivity
     * is active.
     */
    private UnfoldTransitionProgressProvider getUnfoldTransitionProgressProviderForActivity(
            StatefulActivity activity) {
        debugPrimaryTaskbar("getUnfoldTransitionProgressProviderForActivity");
        if (!enableUnfoldStateAnimation()) {
            if (activity instanceof QuickstepLauncher ql) {
                return ql.getUnfoldTransitionProgressProvider();
            }
        } else {
            return SystemUiProxy.INSTANCE.get(mBaseContext).getUnfoldTransitionProvider();
        }
        return null;
    }

    /** Creates a {@link TaskbarUIController} to use with non default displays. */
    private TaskbarUIController createTaskbarUIControllerForNonDefaultDisplay(int displayId) {
        debugTaskbarManager("createTaskbarUIControllerForNonDefaultDisplay", displayId);
        BaseContainerInterface<?, ?> containerInterface = OverviewComponentObserver.INSTANCE.get(
                mBaseContext).getContainerInterface(displayId);
        if (containerInterface != null) {
            RecentsViewContainer container = containerInterface.getCreatedContainer();
            if (container instanceof RecentsWindowManager) {
                return createTaskbarUIControllerForRecentsViewContainer(container, displayId);
            }
        }
        return new TaskbarUIController();
    }

    /**
     * Creates a {@link TaskbarUIController} to use while the given StatefulActivity is active.
     */
    private TaskbarUIController createTaskbarUIControllerForRecentsViewContainer(
            RecentsViewContainer container, int displayId) {
        debugTaskbarManager("createTaskbarUIControllerForRecentsViewContainer", displayId);
        if (!isExternalDisplay(displayId)
                && mActivity instanceof QuickstepLauncher quickstepLauncher) {
            // If 1P Launcher is default, always use LauncherTaskbarUIController, regardless of
            // whether the recents container is NexusLauncherActivity or RecentsWindowManager. This
            // is only applicable for primary displays. In case of foldables both displays have
            // primary display ID and only one of them is primary at a given time, the other one is
            // inactive or has limited functionality (has different display ID in that case).
            return new LauncherTaskbarUIController(quickstepLauncher);
        }
        // If a 3P Launcher is default, always use FallbackTaskbarUIController regardless of
        // whether the recents container is RecentsActivity or RecentsWindowManager.
        if (container instanceof RecentsActivity recentsActivity) {
            return new FallbackTaskbarUIController<>(recentsActivity);
        }
        if (container instanceof RecentsWindowManager recentsWindowManager) {
            return new FallbackTaskbarUIController<>(recentsWindowManager);
        }
        return new TaskbarUIController();
    }

    /**
     * This method is called multiple times (ex. initial init, then when user unlocks) in which case
     * we fully want to destroy existing taskbars and create all desired new ones.
     * In other case (folding/unfolding) we don't need to remove and add window.
     */
    public synchronized void recreateTaskbars() {
        for (int i = 0; i < mWindowContexts.size(); i++) {
            int displayId = mWindowContexts.keyAt(i);
            debugTaskbarManager("recreateTaskbars", displayId);
            recreateTaskbarForDisplay(displayId, 0);
        }
    }

    /**
     * This method is called multiple times (ex. initial init, then when user unlocks) in which case
     * we fully want to destroy an existing taskbar for a specified display and create a new one.
     * In other case (folding/unfolding) we don't need to remove and add window.
     */
    @VisibleForTesting
    protected void recreateTaskbarForDisplay(int displayId, int duration) {
        debugTaskbarManager("recreateTaskbarForDisplay: ", displayId);
        Trace.beginSection("recreateTaskbarForDisplay");
        try {
            debugTaskbarManager("recreateTaskbarForDisplay: getting device profile", displayId);
            // TODO (b/381113004): make this display-specific via getWindowContext()
            DeviceProfile dp = getDeviceProfile(displayId);

            // All Apps action is unrelated to navbar unification, so we only need to check DP.
            final boolean isLargeScreenTaskbar = dp != null && dp.isTaskbarPresent;
            mAllAppsActionManager.setTaskbarPresent(isLargeScreenTaskbar);
            debugTaskbarManager("recreateTaskbarForDisplay: destroying taskbar", displayId);
            destroyTaskbarForDisplay(displayId);

            boolean displayExists = getDisplay(displayId) != null;
            boolean isTaskbarEnabled = dp != null && isTaskbarEnabled(dp);
            debugTaskbarManager("recreateTaskbarForDisplay: isTaskbarEnabled=" + isTaskbarEnabled
                    + " [dp != null (i.e. mUserUnlocked)]=" + (dp != null)
                    + " FLAG_HIDE_NAVBAR_WINDOW=" + ENABLE_TASKBAR_NAVBAR_UNIFICATION
                    + " dp.isTaskbarPresent=" + (dp == null ? "null" : dp.isTaskbarPresent)
                    + " displayExists=" + displayExists, displayId);
            if (!isTaskbarEnabled || !isLargeScreenTaskbar || !displayExists) {
                SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.get(mBaseContext);
                systemUiProxy.notifyTaskbarStatus(/* visible */ false, /* stashed */ false);
                systemUiProxy.setHasBubbleBar(false);
                if (!isTaskbarEnabled || !displayExists) {
                    debugTaskbarManager(
                            "recreateTaskbarForDisplay: exiting bc (!isTaskbarEnabled || "
                                    + "!displayExists)",
                            displayId);
                    return;
                }
            }

            TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
            if (enableTaskbarNoRecreate() || taskbar == null) {
                debugTaskbarManager("recreateTaskbarForDisplay: creating taskbar", displayId);
                taskbar = createTaskbarActivityContext(dp, displayId);
                if (taskbar == null) {
                    debugTaskbarManager(
                            "recreateTaskbarForDisplay: new taskbar instance is null!", displayId);
                    return;
                }
            } else {
                debugTaskbarManager("recreateTaskbarForDisplay: updating taskbar device profile",
                        displayId);
                taskbar.updateDeviceProfile(dp);
            }
            TaskbarSharedState sharedState = getSharedStateForDisplay(displayId);
            sharedState.startTaskbarVariantIsTransient = taskbar.isTransientTaskbar();
            sharedState.allAppsVisible = sharedState.allAppsVisible && isLargeScreenTaskbar;
            taskbar.init(sharedState, duration);

            // Non default displays should not use LauncherTaskbarUIController as they shouldn't
            // have access to the Launcher activity.
            if (isExternalDisplay(displayId)) {
                taskbar.setUIController(createTaskbarUIControllerForNonDefaultDisplay(displayId));
            } else if (mRecentsViewContainer != null) {
                taskbar.setUIController(
                        createTaskbarUIControllerForRecentsViewContainer(mRecentsViewContainer,
                                mPrimaryDisplayId));
            }

            if (enableTaskbarNoRecreate()) {
                debugTaskbarManager("recreateTaskbarForDisplay: adding rootView", displayId);
                addTaskbarRootViewToWindow(taskbar);
                FrameLayout taskbarRootLayout = getTaskbarRootLayoutForDisplay(displayId);
                if (taskbarRootLayout != null) {
                    debugTaskbarManager("recreateTaskbarForDisplay: adding root layout", displayId);
                    taskbarRootLayout.removeAllViews();
                    taskbarRootLayout.addView(taskbar.getDragLayer());
                    taskbar.notifyUpdateLayoutParams();
                } else {
                    debugTaskbarManager("recreateTaskbarForDisplay: taskbarRootLayout is null!",
                            displayId);
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    /** Called when the SysUI flags for a given display change. */
    public void onSystemUiFlagsChanged(@SystemUiStateFlags long systemUiStateFlags, int displayId) {
        TaskbarSharedState sharedState = getSharedStateForDisplay(displayId);
        if (DEBUG) {
            Log.d(TAG, "SysUI flags changed: " + formatFlagChange(systemUiStateFlags,
                    sharedState.sysuiStateFlags, QuickStepContract::getSystemUiStateString));
        }
        sharedState.sysuiStateFlags = systemUiStateFlags;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.updateSysuiStateFlags(systemUiStateFlags, false /* fromInit */);
        }
    }

    public void onLongPressHomeEnabled(boolean assistantLongPressEnabled) {
        if (mPrimaryNavButtonController != null) {
            mPrimaryNavButtonController.setAssistantLongPressEnabled(assistantLongPressEnabled);
        }
    }

    /**
     * Sets the flag indicating setup UI is visible
     */
    public void setSetupUIVisible(boolean isVisible) {
        mAllAppsActionManager.setSetupUiVisible(isVisible);
        for (int i = 0; i < mWindowContexts.size(); i++) {
            int displayId = mWindowContexts.keyAt(i);
            getSharedStateForDisplay(displayId).setupUIVisible = isVisible;
            TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
            if (taskbar != null) {
                taskbar.setSetupUIVisible(isVisible);
            }
        }
    }

    /**
     * Sets wallpaper visibility for specific display.
     */
    public void setWallpaperVisible(int displayId, boolean isVisible) {
        getSharedStateForDisplay(displayId).wallpaperVisible = isVisible;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.setWallpaperVisible(isVisible);
        }
    }

    public void checkNavBarModes(int displayId) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.checkNavBarModes();
        }
    }

    public void finishBarAnimations(int displayId) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.finishBarAnimations();
        }
    }

    public void touchAutoDim(int displayId, boolean reset) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.touchAutoDim(reset);
        }
    }

    public void transitionTo(int displayId, @BarTransitions.TransitionMode int barMode,
            boolean animate) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.transitionTo(barMode, animate);
        }
    }

    public void appTransitionPending(boolean pending) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(mPrimaryDisplayId);
        if (taskbar != null) {
            taskbar.appTransitionPending(pending);
        }
    }

    private boolean isTaskbarEnabled(DeviceProfile deviceProfile) {
        return ENABLE_TASKBAR_NAVBAR_UNIFICATION || deviceProfile.isTaskbarPresent;
    }

    public void onRotationProposal(int rotation, boolean isValid) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(mPrimaryDisplayId);
        if (taskbar != null) {
            taskbar.onRotationProposal(rotation, isValid);
        }
    }

    public void disableNavBarElements(int displayId, int state1, int state2, boolean animate) {
        TaskbarSharedState sharedState = getSharedStateForDisplay(displayId);
        sharedState.disableNavBarDisplayId = displayId;
        sharedState.disableNavBarState1 = state1;
        sharedState.disableNavBarState2 = state2;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.disableNavBarElements(displayId, state1, state2, animate);
        }
    }

    public void onSystemBarAttributesChanged(int displayId, int behavior) {
        TaskbarSharedState sharedState = getSharedStateForDisplay(displayId);
        sharedState.systemBarAttrsDisplayId = displayId;
        sharedState.systemBarAttrsBehavior = behavior;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.onSystemBarAttributesChanged(displayId, behavior);
        }
    }

    public void onTransitionModeUpdated(int barMode, boolean checkBarModes) {
        for (int i = 0; i < mWindowContexts.size(); i++) {
            int displayId = mWindowContexts.keyAt(i);
            getSharedStateForDisplay(displayId).barMode = barMode;
            TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
            if (taskbar != null) {
                taskbar.onTransitionModeUpdated(barMode, checkBarModes);
            }
        }
    }

    public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
        for (int i = 0; i < mWindowContexts.size(); i++) {
            int displayId = mWindowContexts.keyAt(i);
            getSharedStateForDisplay(displayId).navButtonsDarkIntensity = darkIntensity;
            TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
            if (taskbar != null) {
                taskbar.onNavButtonsDarkIntensityChanged(darkIntensity);
            }
        }
    }

    public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {
        TaskbarSharedState sharedState = getSharedStateForDisplay(displayId);
        sharedState.mLumaSamplingDisplayId = displayId;
        sharedState.mIsLumaSamplingEnabled = enable;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.onNavigationBarLumaSamplingEnabled(displayId, enable);
        }
    }

    /**
     * Signal from SysUI indicating that a non-mirroring display was just connected to the
     * primary device or a previously mirroring display is switched to extended mode.
     */
    @Override
    public void onDisplayAddSystemDecorations(int displayId) {
        debugTaskbarManager("onDisplayAddSystemDecorations: ", displayId);
        Display display = getDisplay(displayId);
        if (display == null) {
            debugTaskbarManager("onDisplayAddSystemDecorations: can't find display!", displayId);
            return;
        }

        if (!isExternalDisplay(displayId)) {
            debugTaskbarManager(
                    "onDisplayAddSystemDecorations: not an external display! | "
                            + "isExternalDisplay=" + isExternalDisplay(displayId), displayId);
            return;
        }
        debugTaskbarManager("onDisplayAddSystemDecorations: creating new windowContext!",
                displayId);
        Context newWindowContext = createWindowContext(displayId);
        if (newWindowContext != null) {
            debugTaskbarManager("onDisplayAddSystemDecorations: add new windowContext to map!",
                    displayId);
            WindowManager wm = newWindowContext.getSystemService(WindowManager.class);
            if (wm == null || !wm.shouldShowSystemDecors(displayId)) {
                String wmStatus = wm == null ? "WindowManager is null!" : "WindowManager exists";
                boolean showDecor = wm != null && wm.shouldShowSystemDecors(displayId);
                debugTaskbarManager(
                        "onDisplayAddSystemDecorations:\n\t" + wmStatus + "\n\tshowSystemDecors="
                                + showDecor, displayId);
                return;
            }
            addWindowContextToMap(displayId, newWindowContext);
            debugTaskbarManager("onDisplayAddSystemDecorations: creating RootLayout!", displayId);

            createExternalDeviceProfile(displayId);

            debugTaskbarManager("onDisplayAddSystemDecorations: creating RootLayout!", displayId);
            createTaskbarRootLayout(displayId);

            debugTaskbarManager("onDisplayAddSystemDecorations: creating NavButtonController!",
                    displayId);
            createNavButtonController(displayId);

            debugTaskbarManager(
                    "onDisplayAddSystemDecorations: createAndRegisterComponentCallbacks!",
                    displayId);
            createAndRegisterComponentCallbacks(displayId);

            debugTaskbarManager(
                    "onDisplayAddSystemDecorations: addRecreationListener!", displayId);
            addRecreationListener(displayId);

            debugTaskbarManager("onDisplayAddSystemDecorations: recreateTaskbarForDisplay!",
                    displayId);
            recreateTaskbarForDisplay(displayId, 0);
        } else {
            debugTaskbarManager("onDisplayAddSystemDecorations: newWindowContext is NULL!",
                    displayId);
        }

        debugTaskbarManager("onDisplayAddSystemDecorations: finished!", displayId);
    }

    /**
     * Signal from SysUI indicating that a previously connected non-mirroring display was just
     * removed from the primary device.
     */
    @Override
    public void onDisplayRemoved(int displayId) {
        debugTaskbarManager("onDisplayRemoved: ", displayId);
        if (!isExternalDisplay(displayId)) {
            debugTaskbarManager(
                    "onDisplayRemoved: not an external display! | "
                            + "isExternalDisplay=" + isExternalDisplay(displayId), displayId);
            return;
        }

        Context windowContext = getWindowContext(displayId);
        if (windowContext != null) {
            debugTaskbarManager("onDisplayRemoved: removing NavButtonController!", displayId);
            removeNavButtonController(displayId);

            debugTaskbarManager("onDisplayRemoved: removeAndUnregisterComponentCallbacks!",
                    displayId);
            removeAndUnregisterComponentCallbacks(displayId);

            debugTaskbarManager("onDisplayRemoved: removeRecreationListener!", displayId);
            removeRecreationListener(displayId);

            debugTaskbarManager("onDisplayRemoved: removing DeviceProfile from map!", displayId);
            removeDeviceProfileFromMap(displayId);

            debugTaskbarManager("onDisplayRemoved: destroying Taskbar!", displayId);
            destroyTaskbarForDisplay(displayId);

            debugTaskbarManager("onDisplayRemoved: removing WindowContext from map!", displayId);
            removeWindowContextFromMap(displayId);

            debugTaskbarManager("onDisplayRemoved: destroying SharedState from map!", displayId);
            destroySharedStateForDisplay(displayId);

            debugTaskbarManager("onDisplayRemoved: finished!", displayId);
        } else {
            debugTaskbarManager("onDisplayRemoved: windowContext is null!", displayId);
        }
    }

    /**
     * Signal from SysUI indicating that system decorations should be removed from the display.
     */
    @Override
    public void onDisplayRemoveSystemDecorations(int displayId) {
        // The display mirroring starts. The handling logic is the same as when removing a
        // display.
        onDisplayRemoved(displayId);
    }

    private void removeActivityCallbacksAndListeners() {
        if (mActivity != null) {
            mActivity.removeOnDeviceProfileChangeListener(mDebugActivityDeviceProfileChanged);
            debugPrimaryTaskbar("unregistering activity lifecycle callbacks");
            mActivity.removeEventCallback(EVENT_DESTROYED, mActivityOnDestroyCallback);
            UnfoldTransitionProgressProvider unfoldTransitionProgressProvider =
                    getUnfoldTransitionProgressProviderForActivity(mActivity);
            if (unfoldTransitionProgressProvider != null) {
                unfoldTransitionProgressProvider.removeCallback(mUnfoldTransitionProgressListener);
            }
        }
    }

    /**
     * Called when the manager is no longer needed
     */
    public void destroy() {
        debugPrimaryTaskbar("TaskbarManager#destroy()");
        mRecentsViewContainer = null;
        debugPrimaryTaskbar("destroy: removing activity callbacks");
        DesktopVisibilityController.INSTANCE.get(
                mPrimaryWindowContext).unregisterTaskbarDesktopModeListener(
                mTaskbarDesktopModeListener);
        removeActivityCallbacksAndListeners();
        destroySharedStateForAllDisplays();
        if (mGrowthBroadcastReceiver != null) {
            mGrowthBroadcastReceiver.unregisterReceiverSafely();
        }

        removeRecreationListener(mPrimaryDisplayId);

        if (LawnchairApp.isRecentsEnabled()) {
            SettingsCache.INSTANCE.get(mPrimaryWindowContext)
                .unregister(USER_SETUP_COMPLETE_URI, mOnSettingsChangeListener);
            SettingsCache.INSTANCE.get(mPrimaryWindowContext)
                .unregister(NAV_BAR_KIDS_MODE, mOnSettingsChangeListener);
        }
        // Lawnchair-TODO: DesktopExperienceFlags.ENABLE_SYS_DECORS_CALLBACKS_VIA_WM.isTrue()
        //                && DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue()
        if (false) {
            mDisplaysWithDecorationsRepositoryCompat.unregisterDisplayDecorationListener(this);
        } else {
            SystemDecorationChangeObserver.getINSTANCE().get(mPrimaryWindowContext)
                    .unregisterDisplayDecorationListener(this);
        }
        debugPrimaryTaskbar("destroy: unregistering component callbacks");
        removeAndUnregisterComponentCallbacks(mPrimaryDisplayId);
        mShutdownReceiver.unregisterReceiverSafely();
        if (mTaskStackListener != null) {
            mTaskStackListener.unregisterListener();
        }

        debugPrimaryTaskbar("destroy: destroying all taskbars!");
        removeWindowContextFromMap(mPrimaryDisplayId);
        destroyAllTaskbars();
        debugPrimaryTaskbar("destroy: finished!");
    }

    private boolean eligibleForPerceptibleTasks() {
        // Perceptible tasks feature (oom boosting) is eligible for android PC devices, and
        // other android devices that supports free form windows
        //
        // - isAndroidPC is set per device (in this case, desktop devices)
        // - supportsFreeformWindowsManagement is dynamic, and is to be used for the use-case where
        // user plugs in their device to external displays
        // Lawnchair-TODO: AM Flags, perceptibleTasks
        //return Flags.perceptibleTasks()
        //        && (mIsAndroidPC || mSupportsFreeformWindowsManagement);
        return false;
    }

    public @Nullable TaskbarActivityContext getCurrentActivityContext() {
        return getTaskbarForDisplay(mPrimaryDisplayId);
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarManager:");
        // iterate through taskbars and do the dump for each
        for (Entry<Integer, TaskbarActivityContext> entry : mTaskbars.entrySet()) {
            int displayId = entry.getKey();
            TaskbarActivityContext taskbar = entry.getValue();
            pw.println(prefix + "\tTaskbar at display " + displayId + ":");
            if (taskbar == null) {
                pw.println(prefix + "\t\tTaskbarActivityContext: null");
            } else {
                taskbar.dumpLogs(prefix + "\t\t", pw);
            }
        }
    }

    private void addTaskbarRootViewToWindow(@NonNull TaskbarActivityContext taskbar) {
        int displayId = taskbar.getDisplayId();
        debugTaskbarManager("addTaskbarRootViewToWindow:", displayId);
        if (!enableTaskbarNoRecreate()) {
            debugTaskbarManager("addTaskbarRootViewToWindow: taskbar null", displayId);
            return;
        }

        if (getDisplay(displayId) == null) {
            debugTaskbarManager("addTaskbarRootViewToWindow: display null", displayId);
            return;
        }

        if (!isTaskbarRootLayoutAddedForDisplay(displayId)) {
            FrameLayout rootLayout = getTaskbarRootLayoutForDisplay(displayId);
            WindowManager windowManager = getWindowManager(displayId);
            if (rootLayout != null && windowManager != null) {
                windowManager.addView(rootLayout, taskbar.getWindowLayoutParams());
                mAddedRootLayouts.put(displayId, true);
            } else {
                String rootLayoutStatus =
                        (rootLayout == null) ? "rootLayout is NULL!" : "rootLayout exists!";
                String wmStatus = (windowManager == null) ? "windowManager is NULL!"
                        : "windowManager exists!";
                debugTaskbarManager(
                        "addTaskbarRootViewToWindow: \n\t" + rootLayoutStatus + "\n\t" + wmStatus,
                        displayId);
            }
        } else {
            debugTaskbarManager("addTaskbarRootViewToWindow: rootLayout already added!", displayId);
        }
    }

    private void removeTaskbarRootViewFromWindow(int displayId) {
        debugTaskbarManager("removeTaskbarRootViewFromWindow", displayId);
        FrameLayout rootLayout = getTaskbarRootLayoutForDisplay(displayId);
        if (!enableTaskbarNoRecreate() || rootLayout == null) {
            return;
        }

        WindowManager windowManager = getWindowManager(displayId);
        if (isTaskbarRootLayoutAddedForDisplay(displayId) && windowManager != null) {
            windowManager.removeViewImmediate(rootLayout);
            mAddedRootLayouts.put(displayId, false);
            removeTaskbarRootLayoutFromMap(displayId);
        } else {
            debugTaskbarManager("removeTaskbarRootViewFromWindow: WindowManager is null",
                    displayId);
        }
    }

    /**
     * Returns the {@link TaskbarUIController} associated with the given display ID.
     * TODO(b/395061396): Remove this method when overview in widow is enabled.
     *
     * @param displayId The ID of the display to retrieve the taskbar for.
     * @return The {@link TaskbarUIController} for the specified display, or
     * {@code null} if no taskbar is associated with that display.
     */
    @Nullable
    public TaskbarUIController getUIControllerForDisplay(int displayId) {
        TaskbarActivityContext taskbarActivityContext = getTaskbarForDisplay(displayId);
        if (taskbarActivityContext == null) {
            return null;
        }

        return taskbarActivityContext.getControllers().uiController;
    }

    /**
     * Retrieves whether RootLayout was added to window for specific display, or false if no
     * such mapping has been made.
     *
     * @param displayId The ID of the display for which to retrieve the taskbar root layout.
     * @return if RootLayout was added to window {@link Boolean} for a display or {@code false}.
     */
    private boolean isTaskbarRootLayoutAddedForDisplay(int displayId) {
        return mAddedRootLayouts.get(displayId);
    }

    /**
     * Returns the {@link TaskbarActivityContext} associated with the given display ID.
     *
     * @param displayId The ID of the display to retrieve the taskbar for.
     * @return The {@link TaskbarActivityContext} for the specified display, or
     * {@code null} if no taskbar is associated with that display.
     */
    public TaskbarActivityContext getTaskbarForDisplay(int displayId) {
        return mTaskbars.get(displayId);
    }

    private TaskbarSharedState getSharedStateForDisplay(int displayId) {
        TaskbarSharedState sharedState = mTaskbarSharedStates.getOrDefault(displayId,
                new TaskbarSharedState());
        mTaskbarSharedStates.put(displayId, sharedState);

        // Verify if shared state is properly initialised. Sometimes it can only be initialised with
        // subsequent access. For example, onSystemUiFlagsChanged gets called before
        // recreateTaskbarForDisplay when display is added resulting into windowContext being null.
        if (sharedState.taskbarSystemActionPendingIntent == null && mWindowContexts.contains(
                displayId)) {
            debugTaskbarManager("getSharedStateForDisplay: initialising shared state", displayId);

            Context windowContext = mWindowContexts.get(displayId);
            SimpleBroadcastReceiver broadcastReceiver = new SimpleBroadcastReceiver(windowContext,
                    UI_HELPER_EXECUTOR, (intent) -> showTaskbarFromBroadcast(intent, displayId));
            mTaskbarBroadcastReceivers.put(displayId, broadcastReceiver);

            sharedState.taskbarSystemActionPendingIntent = PendingIntent.getBroadcast(windowContext,
                    SYSTEM_ACTION_ID_TASKBAR,
                    new Intent(ACTION_SHOW_TASKBAR).setPackage(windowContext.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            UI_HELPER_EXECUTOR.execute(
                    () -> broadcastReceiver.register(RECEIVER_NOT_EXPORTED, ACTION_SHOW_TASKBAR));
        }

        return sharedState;
    }

    /** Should only be called when the TaskbarManager class is being destroyed. */
    private void destroySharedStateForAllDisplays() {
        debugPrimaryTaskbar("getSharedStateForDisplay: destroying all shared state",
                /* verbose= */ false);

        for (SimpleBroadcastReceiver broadcastReceiver : mTaskbarBroadcastReceivers.values()) {
            broadcastReceiver.unregisterReceiverSafely();
        }

        mTaskbarBroadcastReceivers.clear();
        mTaskbarSharedStates.clear();
    }

    /** Should be called when the taskbar is not going to be recreated, example display removed. */
    private void destroySharedStateForDisplay(int displayId) {
        debugTaskbarManager("getSharedStateForDisplay: destroying shared state", displayId);
        SimpleBroadcastReceiver broadcastReceiver = mTaskbarBroadcastReceivers.remove(displayId);
        if (broadcastReceiver != null) {
            broadcastReceiver.unregisterReceiverSafely();
        }

        mTaskbarSharedStates.remove(displayId);
    }

    /**
     * Creates a {@link TaskbarActivityContext} for the given display and adds it to the map.
     *
     * @param dp        The {@link deviceprofile} for the display.
     * @param displayId The ID of the display.
     */
    private @Nullable TaskbarActivityContext createTaskbarActivityContext(DeviceProfile dp,
            int displayId) {
        Display display = getDisplay(displayId);
        if (display == null) {
            debugTaskbarManager("createTaskbarActivityContext: display null", displayId);
            return null;
        }

        Context navigationBarPanelContext = null;
        if (ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
            navigationBarPanelContext = mBaseContext.createWindowContext(display,
                    TYPE_NAVIGATION_BAR_PANEL, null);
        }

        TaskbarActivityContext newTaskbar = new TaskbarActivityContext(displayId,
                getWindowContext(displayId), navigationBarPanelContext, dp,
                getNavButtonController(displayId), mUnfoldProgressProvider,
                !isExternalDisplay(displayId), getPrimaryDisplayId(),
                SystemUiProxy.INSTANCE.get(mBaseContext));

        addTaskbarToMap(displayId, newTaskbar);
        return newTaskbar;
    }

    /**
     * Creates a {@link deviceprofile} for the given display and adds it to the map.
     *
     * @param displayId The ID of the display.
     */
    private void createExternalDeviceProfile(int displayId) {
        if (!mUserUnlocked || displayId == mPrimaryDisplayId) {
            return;
        }

        InvariantDeviceProfile idp = LauncherAppState.getIDP(mPrimaryWindowContext);
        if (idp == null) {
            return;
        }

        Context displayContext = getWindowContext(displayId);
        if (displayContext == null) {
            return;
        }

        DeviceProfile externalDeviceProfile = idp.createDeviceProfileForSecondaryDisplay(
                displayContext);
        mExternalDeviceProfiles.put(displayId, externalDeviceProfile);
    }

    /**
     * Gets a {@link deviceprofile} for the given displayId.
     *
     * @param displayId The ID of the display.
     */
    private @Nullable DeviceProfile getDeviceProfile(int displayId) {
        if (!mUserUnlocked) {
            return null;
        }

        InvariantDeviceProfile idp = LauncherAppState.getIDP(mPrimaryWindowContext);
        if (idp == null) {
            return null;
        }

        if (!isExternalDisplay(displayId)) {
            return idp.getDeviceProfile(mPrimaryWindowContext);
        }

        return mExternalDeviceProfiles.get(displayId);
    }

    /**
     * Removes the {@link deviceprofile} associated with the given display ID from the map.
     *
     * @param displayId The ID of the display for which to remove the taskbar.
     */
    private void removeDeviceProfileFromMap(int displayId) {
        mExternalDeviceProfiles.delete(displayId);
    }

    private void addRecreationListener(int displayId) {
        if (!mUserUnlocked) {
            return;
        }

        DisplayController.INSTANCE.get(mPrimaryWindowContext).addChangeListenerForDisplay(
                mRecreationListener, displayId);
    }

    private void removeRecreationListener(int displayId) {
        if (!mUserUnlocked) {
            return;
        }

        DisplayController.INSTANCE.get(mPrimaryWindowContext).removeChangeListenerForDisplay(
                mRecreationListener, displayId);
    }

    /**
     * Create {@link ComponentCallbacks} for the given display and register it to the relevant
     * WindowContext. For external displays, populate maps.
     *
     * @param displayId The ID of the display.
     */
    private void createAndRegisterComponentCallbacks(int displayId) {
        debugTaskbarManager("createAndRegisterComponentCallbacks", displayId);
        ComponentCallbacks callbacks = new ComponentCallbacks() {
            private Configuration mOldConfig =
                    getWindowContext(displayId).getResources().getConfiguration();

            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                Trace.instantForTrack(Trace.TRACE_TAG_APP, "TaskbarManager",
                        "onConfigurationChanged: " + newConfig);
                debugTaskbarManager("onConfigurationChanged: " + newConfig, displayId);

                DeviceProfile dp = getDeviceProfile(displayId);
                int configDiff = mOldConfig.diff(newConfig) & ~SKIP_RECREATE_CONFIG_CHANGES;

                if ((configDiff & ActivityInfo.CONFIG_UI_MODE) != 0) {
                    debugTaskbarManager("onConfigurationChanged: theme changed", displayId);
                    // Only recreate for theme changes, not other UI mode changes such as docking.
                    int oldUiNightMode = (mOldConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    int newUiNightMode = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    if (oldUiNightMode == newUiNightMode) {
                        configDiff &= ~ActivityInfo.CONFIG_UI_MODE;
                    }
                }

                debugTaskbarManager("onConfigurationChanged: | configDiff="
                        + Configuration.configurationDiffToString(configDiff), displayId);
                TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
                if (configDiff != 0 || taskbar == null) {
                    debugTaskbarManager("onConfigurationChanged: call recreateTaskbars", displayId);
                    recreateTaskbarForDisplay(displayId, /* duration= */ 0);
                } else if (dp != null) {
                    // Config change might be handled without re-creating the taskbar
                    if (!isTaskbarEnabled(dp)) {
                        debugPrimaryTaskbar(
                                "onConfigurationChanged: isTaskbarEnabled(dp)=False | "
                                        + "destroyTaskbarForDisplay");
                        destroyTaskbarForDisplay(displayId);
                    } else {
                        debugPrimaryTaskbar("onConfigurationChanged: isTaskbarEnabled(dp)=True");
                        if (ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
                            // Re-initialize for screen size change? Should this be done
                            // by looking at screen-size change flag in configDiff in the
                            // block above?
                            debugPrimaryTaskbar("onConfigurationChanged: call recreateTaskbars");
                            recreateTaskbarForDisplay(displayId, /* duration= */ 0);
                        } else {
                            debugPrimaryTaskbar(
                                    "onConfigurationChanged: updateDeviceProfile for current "
                                            + "taskbar.");
                            taskbar.updateDeviceProfile(dp);
                        }
                    }
                } else {
                    taskbar.onConfigurationChanged(configDiff);
                }
                mOldConfig = new Configuration(newConfig);
                // reset taskbar was pinned value, so we don't automatically unstash taskbar upon
                // user unfolding the device.
                getSharedStateForDisplay(displayId).setTaskbarWasPinned(false);
            }

            @Override
            public void onLowMemory() {
            }
        };
        if (!isExternalDisplay(displayId)) {
            mPrimaryComponentCallbacks = callbacks;
            mPrimaryWindowContext.registerComponentCallbacks(callbacks);
        } else {
            mComponentCallbacks.put(displayId, callbacks);
            getWindowContext(displayId).registerComponentCallbacks(callbacks);
        }
    }

    /**
     * Unregister {@link ComponentCallbacks} for the given display from its WindowContext. For
     * external displays, remove from the map.
     *
     * @param displayId The ID of the display.
     */
    private void removeAndUnregisterComponentCallbacks(int displayId) {
        if (!isExternalDisplay(displayId)) {
            mPrimaryWindowContext.unregisterComponentCallbacks(mPrimaryComponentCallbacks);
        } else {
            ComponentCallbacks callbacks = mComponentCallbacks.get(displayId);
            getWindowContext(displayId).unregisterComponentCallbacks(callbacks);
            mComponentCallbacks.delete(displayId);
        }
    }

    /**
     * Creates a {@link TaskbarNavButtonController} for the given display and adds it to the map
     * if it doesn't already exist.
     *
     * @param displayId The ID of the display
     */
    private void createNavButtonController(int displayId) {
        if (!isExternalDisplay(displayId)) {
            mPrimaryNavButtonController = new TaskbarNavButtonController(
                    mPrimaryWindowContext,
                    mNavCallbacks,
                    SystemUiProxy.INSTANCE.get(mBaseContext),
                    new Handler(),
                    new ContextualSearchInvoker(mBaseContext));
        } else {
            TaskbarNavButtonController navButtonController = new TaskbarNavButtonController(
                    getWindowContext(displayId),
                    mNavCallbacks,
                    SystemUiProxy.INSTANCE.get(mBaseContext),
                    new Handler(),
                    new ContextualSearchInvoker(mBaseContext));
            mNavButtonControllers.put(displayId, navButtonController);
        }
    }

    private TaskbarNavButtonController getNavButtonController(int displayId) {
        return (!isExternalDisplay(displayId)) ? mPrimaryNavButtonController
                : mNavButtonControllers.get(displayId);
    }

    private void removeNavButtonController(int displayId) {
        if (!isExternalDisplay(displayId)) {
            mPrimaryNavButtonController = null;
        } else {
            mNavButtonControllers.delete(displayId);
        }
    }

    /**
     * Adds the {@link TaskbarActivityContext} associated with the given display ID to taskbar
     * map if there is not already a taskbar mapped to that displayId.
     *
     * @param displayId  The ID of the display to retrieve the taskbar for.
     * @param newTaskbar The new {@link TaskbarActivityContext} to add to the map.
     */
    private void addTaskbarToMap(int displayId, TaskbarActivityContext newTaskbar) {
        mTaskbars.putIfAbsent(displayId, newTaskbar);
    }

    /**
     * Removes the taskbar associated with the given display ID from the taskbar map.
     *
     * @param displayId The ID of the display for which to remove the taskbar.
     */
    private void removeTaskbarFromMap(int displayId) {
        mTaskbars.remove(displayId);
    }

    /**
     * Creates {@link FrameLayout} for the taskbar on the specified display and adds it to map.
     *
     * @param displayId The ID of the display for which to create the taskbar root layout.
     */
    private void createTaskbarRootLayout(int displayId) {
        debugTaskbarManager("createTaskbarRootLayout: ", displayId);
        if (!enableTaskbarNoRecreate()) {
            return;
        }

        FrameLayout newTaskbarRootLayout = new FrameLayout(getWindowContext(displayId)) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                debugTaskbarManager("dispatchTouchEvent: ", displayId);
                // The motion events can be outside the view bounds of task bar, and hence
                // manually dispatching them to the drag layer here.
                TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
                if (taskbar != null && taskbar.getDragLayer().isAttachedToWindow()) {
                    return taskbar.getDragLayer().dispatchTouchEvent(ev);
                }
                return super.dispatchTouchEvent(ev);
            }
        };

        debugTaskbarManager("createTaskbarRootLayout: adding to map", displayId);
        addTaskbarRootLayoutToMap(displayId, newTaskbarRootLayout);
    }

    private boolean isDefaultDisplay(int displayId) {
        return displayId == mPrimaryDisplayId;
    }

    /**
     * Retrieves the root layout of the taskbar for the specified display.
     *
     * @param displayId The ID of the display for which to retrieve the taskbar root layout.
     * @return The taskbar root layout {@link FrameLayout} for a given display or {@code null}.
     */
    private FrameLayout getTaskbarRootLayoutForDisplay(int displayId) {
        debugTaskbarManager("getTaskbarRootLayoutForDisplay:", displayId);
        FrameLayout frameLayout = mRootLayouts.get(displayId);
        if (frameLayout != null) {
            return frameLayout;
        } else {
            debugTaskbarManager("getTaskbarRootLayoutForDisplay: rootLayout is null!", displayId);
            return null;
        }
    }

    /**
     * Adds the taskbar root layout {@link FrameLayout} to taskbar map, mapped to display ID.
     *
     * @param displayId  The ID of the display to associate with the taskbar root layout.
     * @param rootLayout The taskbar root layout {@link FrameLayout} to add to the map.
     */
    private void addTaskbarRootLayoutToMap(int displayId, FrameLayout rootLayout) {
        debugTaskbarManager("addTaskbarRootLayoutToMap: ", displayId);
        if (!mRootLayouts.contains(displayId) && rootLayout != null) {
            mRootLayouts.put(displayId, rootLayout);
        }

        debugTaskbarManager(
                "addTaskbarRootLayoutToMap: finished! mRootLayouts.size()=" + mRootLayouts.size(),
                displayId);
    }

    /**
     * Removes taskbar root layout {@link FrameLayout} for given display ID from the taskbar map.
     *
     * @param displayId The ID of the display for which to remove the taskbar root layout.
     */
    private void removeTaskbarRootLayoutFromMap(int displayId) {
        debugTaskbarManager("removeTaskbarRootLayoutFromMap:", displayId);
        if (mRootLayouts.contains(displayId)) {
            mAddedRootLayouts.delete(displayId);
            mRootLayouts.delete(displayId);
        }

        debugTaskbarManager("removeTaskbarRootLayoutFromMap: finished! mRootLayouts.size="
                + mRootLayouts.size(), displayId);
    }

    /**
     * Creates {@link Context} for the taskbar on the specified display.
     *
     * @param displayId The ID of the display for which to create the window context.
     */
    private @Nullable Context createWindowContext(int displayId) {
        debugTaskbarManager("createWindowContext: ", displayId);
        Display display = getDisplay(displayId);
        if (display == null) {
            debugTaskbarManager("createWindowContext: display null!", displayId);
            return null;
        }

        int windowType = TYPE_NAVIGATION_BAR_PANEL;
        if (ENABLE_TASKBAR_NAVBAR_UNIFICATION && !isExternalDisplay(displayId)) {
            windowType = TYPE_NAVIGATION_BAR;
        }
        debugTaskbarManager(
                "createWindowContext: windowType=" + ((windowType == TYPE_NAVIGATION_BAR)
                        ? "TYPE_NAVIGATION_BAR" : "TYPE_NAVIGATION_BAR_PANEL"), displayId);

        return mBaseContext.createWindowContext(display, windowType, null);
    }

    private @Nullable Display getDisplay(int displayId) {
        if (mDisplayManager == null) {
            debugTaskbarManager("cannot get DisplayManager", displayId);
            return null;
        }

        Display display = mDisplayManager.getDisplay(displayId);
        if (display == null) {
            debugTaskbarManager("Cannot get display!", displayId);
            return null;
        }

        return mDisplayManager.getDisplay(displayId);
    }

    /**
     * Retrieves the window context of the taskbar for the specified display.
     *
     * @param displayId The ID of the display for which to retrieve the window context.
     * @return The Window Context {@link Context} for a given display or {@code null}.
     */
    private Context getWindowContext(int displayId) {
        return (!isExternalDisplay(displayId))
                ? mPrimaryWindowContext : mWindowContexts.get(displayId);
    }

    @VisibleForTesting
    public Context getPrimaryWindowContext() {
        return mPrimaryWindowContext;
    }

    /**
     * Retrieves the window manager {@link WindowManager} of the taskbar for the specified display.
     *
     * @param displayId The ID of the display for which to retrieve the window manager.
     * @return The window manager {@link WindowManager} for a given display or {@code null}.
     */
    private @Nullable WindowManager getWindowManager(int displayId) {
        if (!isExternalDisplay(displayId)) {
            debugTaskbarManager("cannot get mPrimaryWindowManager", displayId);
            return mPrimaryWindowManager;
        }

        Context externalDisplayContext = getWindowContext(displayId);
        if (externalDisplayContext == null) {
            debugTaskbarManager("cannot get externalDisplayContext", displayId);
            return null;
        }

        return externalDisplayContext.getSystemService(WindowManager.class);
    }

    /**
     * Adds the window context {@link Context} to taskbar map, mapped to display ID.
     *
     * @param displayId     The ID of the display to associate with the taskbar root layout.
     * @param windowContext The window context {@link Context} to add to the map.
     */
    private void addWindowContextToMap(int displayId, @NonNull Context windowContext) {
        if (!mWindowContexts.contains(displayId)) {
            mWindowContexts.put(displayId, windowContext);
        }
    }

    /**
     * Removes the window context {@link Context} for given display ID from the taskbar map.
     *
     * @param displayId The ID of the display for which to remove the taskbar root layout.
     */
    private void removeWindowContextFromMap(int displayId) {
        if (mWindowContexts.contains(displayId)) {
            mWindowContexts.delete(displayId);
        }
    }

    private boolean isExternalDisplay(int displayId) {
        return DesktopExperienceFlags.ENABLE_TASKBAR_CONNECTED_DISPLAYS.isTrue() && (
                mPrimaryDisplayId != displayId);
    }

    private int getFocusedDisplayId() {
        return SystemUiProxy.INSTANCE.get(mBaseContext).getFocusState().getFocusedDisplayId();
    }

    /**
     * Returns the primary display id associated with this manager.
     */
    public int getPrimaryDisplayId() {
        return mPrimaryDisplayId;
    }

    /**
     * Logs debug information about the TaskbarManager for primary display.
     *
     * @param debugReason A string describing the reason for the debug log.
     * @param displayId   The ID of the display for which to log debug information.
     */
    public void debugTaskbarManager(String debugReason, int displayId) {
        StringJoiner log = new StringJoiner("\n");
        log.add(debugReason + " displayId=" + displayId + " isDefaultDisplay=" + isDefaultDisplay(
                displayId));
        Log.d(TAG, log.toString());
    }

    /**
     * Logs verbose debug information about the TaskbarManager for primary display.
     *
     * @param debugReason A string describing the reason for the debug log.
     * @param displayId   The ID of the display for which to log debug information.
     * @param verbose     Indicates whether or not to debug with detail.
     */
    private void debugTaskbarManager(String debugReason, int displayId, boolean verbose) {
        StringJoiner log = new StringJoiner("\n");
        log.add(debugReason + " displayId=" + displayId + " isDefaultDisplay=" + isDefaultDisplay(
                displayId));
        if (verbose) {
            generateVerboseLogs(log, displayId);
        }
        Log.d(TAG, log.toString());
    }

    /**
     * Logs debug information about the TaskbarManager for primary display.
     *
     * @param debugReason A string describing the reason for the debug log.
     */
    private void debugPrimaryTaskbar(String debugReason) {
        debugTaskbarManager(debugReason, mPrimaryDisplayId, false);
    }

    /**
     * Logs debug information about the TaskbarManager for primary display.
     *
     * @param debugReason A string describing the reason for the debug log.
     */
    public void debugPrimaryTaskbar(String debugReason, boolean verbose) {
        debugTaskbarManager(debugReason, mPrimaryDisplayId, verbose);
    }

    /** Creates a {@link PendingIntent} for showing / hiding the all apps UI. */
    public PendingIntent createAllAppsPendingIntent(Executor uiExecutor) {
        return new PendingIntent(new AllAppsIntentSender(uiExecutor, this));
    }

    /**
     * Logs verbose debug information about the TaskbarManager for a specific display.
     */
    private void generateVerboseLogs(StringJoiner log, int displayId) {
        boolean activityTaskbarPresent = mActivity != null
                && mActivity.getDeviceProfile().isTaskbarPresent;
        // TODO (b/381113004): make this display-specific via getWindowContext()
        Context windowContext = mPrimaryWindowContext;
        if (windowContext == null) {
            log.add("windowContext is null!");
            return;
        }

        boolean contextTaskbarPresent = false;
        if (mUserUnlocked) {
            DeviceProfile dp = getDeviceProfile(displayId);
            contextTaskbarPresent = dp != null && dp.isTaskbarPresent;
        }
        if (activityTaskbarPresent == contextTaskbarPresent) {
            log.add("mActivity and mWindowContext agree taskbarIsPresent=" + contextTaskbarPresent);
            Log.d(TAG, log.toString());
            return;
        }

        log.add("mActivity & mWindowContext device profiles have different values, add more logs.");

        log.add("\tmActivity logs:");
        log.add("\t\tmActivity=" + mActivity);
        if (mActivity != null) {
            log.add("\t\tmActivity.getResources().getConfiguration()="
                    + mActivity.getResources().getConfiguration());
            log.add("\t\tmActivity.getDeviceProfile().isTaskbarPresent="
                    + activityTaskbarPresent);
        }
        log.add("\tWindowContext logs:");
        log.add("\t\tWindowContext=" + windowContext);
        log.add("\t\tWindowContext.getResources().getConfiguration()="
                + windowContext.getResources().getConfiguration());
        if (mUserUnlocked) {
            log.add("\t\tgetDeviceProfile(mPrimaryWindowContext).isTaskbarPresent="
                    + contextTaskbarPresent);
        } else {
            log.add("\t\tCouldn't get DeviceProfile because !mUserUnlocked");
        }
    }

    private final DeviceProfile.OnDeviceProfileChangeListener mDebugActivityDeviceProfileChanged =
            dp -> debugPrimaryTaskbar("mActivity onDeviceProfileChanged", true);

    /** Use weak reference to avoid leaking TIS via {@link TaskbarManagerImpl} */
    private static class AllAppsIntentSender extends IIntentSender.Stub {

        private final Executor mUiExecutor;
        private final WeakReference<TaskbarManagerImpl> mWeakTaskbarManager;

        AllAppsIntentSender(Executor uiExecutor, TaskbarManagerImpl taskbarManager) {
            mUiExecutor = uiExecutor;
            mWeakTaskbarManager = new WeakReference<>(taskbarManager);
        }

        @Override
        public void send(int i, Intent intent, String s, IBinder iBinder,
                IIntentReceiver iIntentReceiver, String s1, Bundle bundle) {
            TaskbarManagerImpl taskbarManager = mWeakTaskbarManager.get();
            if (taskbarManager == null) {
                return;
            }
            mUiExecutor.execute(taskbarManager::toggleAllAppsSearch);
        }
    };
}
