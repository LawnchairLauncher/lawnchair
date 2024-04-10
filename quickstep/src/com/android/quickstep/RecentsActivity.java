/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep;

import static android.os.Trace.TRACE_TAG_APP;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;

import static com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.STATUS_BAR_TRANSITION_PRE_DELAY;
import static com.android.launcher3.testing.shared.TestProtocol.LAUNCHER_ACTIVITY_STOPPED_MESSAGE;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_STATE_ORDINAL;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;
import static com.android.quickstep.TaskUtils.taskIsATargetWithMode;
import static com.android.quickstep.TaskViewUtils.createRecentsWindowAnimator;
import static com.android.window.flags.Flags.enableDesktopWindowingMode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Trace;
import android.view.Display;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl.Transaction;
import android.view.View;
import android.window.RemoteTransition;
import android.window.SplashScreen;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAnimationRunner;
import com.android.launcher3.LauncherAnimationRunner.AnimationResult;
import com.android.launcher3.LauncherAnimationRunner.RemoteAnimationFactory;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.desktop.DesktopRecentsTransitionController;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.FallbackTaskbarUIController;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ActivityTracker;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.ScrimView;
import com.android.quickstep.fallback.FallbackRecentsStateController;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.fallback.RecentsDragLayer;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.util.RecentsAtomicAnimationFactory;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.TISBindHelper;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/**
 * A recents activity that shows the recently launched tasks as swipable task cards.
 * See {@link com.android.quickstep.views.RecentsView}.
 */
public final class RecentsActivity extends StatefulActivity<RecentsState> {
    private static final String TAG = "RecentsActivity";

    public static final ActivityTracker<RecentsActivity> ACTIVITY_TRACKER =
            new ActivityTracker<>();

    private Handler mUiHandler = new Handler(Looper.getMainLooper());

    private static final long HOME_APPEAR_DURATION = 250;
    private static final long RECENTS_ANIMATION_TIMEOUT = 1000;

    private RecentsDragLayer mDragLayer;
    private ScrimView mScrimView;
    private FallbackRecentsView mFallbackRecentsView;
    private OverviewActionsView mActionsView;
    private TISBindHelper mTISBindHelper;
    private @Nullable FallbackTaskbarUIController mTaskbarUIController;

    private StateManager<RecentsState> mStateManager;

    // Strong refs to runners which are cleared when the activity is destroyed
    private RemoteAnimationFactory mActivityLaunchAnimationRunner;

    // For handling degenerate cases where starting an activity doesn't actually trigger the remote
    // animation callback
    private final Handler mHandler = new Handler();
    private final Runnable mAnimationStartTimeoutRunnable = this::onAnimationStartTimeout;
    private SplitSelectStateController mSplitSelectStateController;
    @Nullable
    private DesktopRecentsTransitionController mDesktopRecentsTransitionController;

    /**
     * Init drag layer and overview panel views.
     */
    protected void setupViews() {
        inflateRootView(R.layout.fallback_recents_activity);
        setContentView(getRootView());
        mDragLayer = findViewById(R.id.drag_layer);
        mScrimView = findViewById(R.id.scrim_view);
        mFallbackRecentsView = findViewById(R.id.overview_panel);
        mActionsView = findViewById(R.id.overview_actions_view);
        getRootView().getSysUiScrim().getSysUIProgress().updateValue(0);
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.get(this);
        mSplitSelectStateController =
                new SplitSelectStateController(this, mHandler, getStateManager(),
                        null /* depthController */, getStatsLogManager(),
                        systemUiProxy, RecentsModel.INSTANCE.get(this),
                        null /*activityBackCallback*/);
        mDragLayer.recreateControllers();
        if (enableDesktopWindowingMode()) {
            mDesktopRecentsTransitionController = new DesktopRecentsTransitionController(
                    getStateManager(), systemUiProxy, getIApplicationThread(),
                    null /* depthController */
            );
        }
        mFallbackRecentsView.init(mActionsView, mSplitSelectStateController,
                mDesktopRecentsTransitionController);

        mTISBindHelper = new TISBindHelper(this, this::onTISConnected);
    }

    private void onTISConnected(TouchInteractionService.TISBinder binder) {
        TaskbarManager taskbarManager = binder.getTaskbarManager();
        if (taskbarManager != null) {
            taskbarManager.setActivity(this);
        }
    }

    @Override
    public void runOnBindToTouchInteractionService(Runnable r) {
        mTISBindHelper.runOnBindToTouchInteractionService(r);
    }

    public void setTaskbarUIController(FallbackTaskbarUIController taskbarUIController) {
        mTaskbarUIController = taskbarUIController;
    }

    public FallbackTaskbarUIController getTaskbarUIController() {
        return mTaskbarUIController;
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        onHandleConfigurationChanged();
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ACTIVITY_TRACKER.handleNewIntent(this);
    }

    @Override
    protected void onHandleConfigurationChanged() {
        initDeviceProfile();

        AbstractFloatingView.closeOpenViews(this, true,
                AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
        dispatchDeviceProfileChanged();

        reapplyUi();
        mDragLayer.recreateControllers();
    }

    /**
     * Generate the device profile to use in this activity.
     * @return device profile
     */
    protected DeviceProfile createDeviceProfile() {
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(this).getDeviceProfile(this);

        // In case we are reusing IDP, create a copy so that we don't conflict with Launcher
        // activity.
        return (mDragLayer != null) && isInMultiWindowMode()
                ? dp.getMultiWindowProfile(this, getMultiWindowDisplaySize())
                : dp.copy(this);
    }

    @Override
    public BaseDragLayer getDragLayer() {
        return mDragLayer;
    }

    public ScrimView getScrimView() {
        return mScrimView;
    }

    @Override
    public <T extends View> T getOverviewPanel() {
        return (T) mFallbackRecentsView;
    }

    public OverviewActionsView getActionsView() {
        return mActionsView;
    }

    @Override
    public void returnToHomescreen() {
        super.returnToHomescreen();
        // TODO(b/137318995) This should go home, but doing so removes freeform windows
    }

    /**
     * Called if the remote animation callback from #getActivityLaunchOptions() hasn't called back
     * in a reasonable time due to a conflict with the recents animation.
     */
    private void onAnimationStartTimeout() {
        if (mActivityLaunchAnimationRunner != null) {
            mActivityLaunchAnimationRunner.onAnimationCancelled();
        }
    }

    @Override
    public ActivityOptionsWrapper getActivityLaunchOptions(final View v, @Nullable ItemInfo item) {
        if (!(v instanceof TaskView)) {
            return super.getActivityLaunchOptions(v, item);
        }

        final TaskView taskView = (TaskView) v;
        final RecentsView recentsView = taskView.getRecentsView();
        if (recentsView == null) {
            return super.getActivityLaunchOptions(v, item);
        }

        RunnableList onEndCallback = new RunnableList();

        mActivityLaunchAnimationRunner = new RemoteAnimationFactory() {
            @Override
            public void onAnimationStart(int transit, RemoteAnimationTarget[] appTargets,
                    RemoteAnimationTarget[] wallpaperTargets,
                    RemoteAnimationTarget[] nonAppTargets, AnimationResult result) {
                mHandler.removeCallbacks(mAnimationStartTimeoutRunnable);
                AnimatorSet anim = composeRecentsLaunchAnimator(recentsView, taskView, appTargets,
                        wallpaperTargets, nonAppTargets);
                anim.addListener(resetStateListener());
                result.setAnimation(anim, RecentsActivity.this, onEndCallback::executeAllAndDestroy,
                        true /* skipFirstFrame */);
            }

            @Override
            public void onAnimationCancelled() {
                mHandler.removeCallbacks(mAnimationStartTimeoutRunnable);
                onEndCallback.executeAllAndDestroy();
            }
        };

        final LauncherAnimationRunner wrapper = new LauncherAnimationRunner(
                mUiHandler, mActivityLaunchAnimationRunner, true /* startAtFrontOfQueue */);
        final ActivityOptions options = ActivityOptions.makeRemoteAnimation(
                new RemoteAnimationAdapter(wrapper, RECENTS_LAUNCH_DURATION,
                        RECENTS_LAUNCH_DURATION - STATUS_BAR_TRANSITION_DURATION
                                - STATUS_BAR_TRANSITION_PRE_DELAY),
                new RemoteTransition(wrapper.toRemoteTransition(), getIApplicationThread(),
                        "LaunchFromRecents"));
        final ActivityOptionsWrapper activityOptions = new ActivityOptionsWrapper(options,
                onEndCallback);
        activityOptions.options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
        activityOptions.options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : Display.DEFAULT_DISPLAY);
        activityOptions.options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        mHandler.postDelayed(mAnimationStartTimeoutRunnable, RECENTS_ANIMATION_TIMEOUT);
        return activityOptions;
    }

    /**
     * Composes the animations for a launch from the recents list if possible.
     */
    private AnimatorSet  composeRecentsLaunchAnimator(
            @NonNull RecentsView recentsView,
            @NonNull TaskView taskView,
            RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpaperTargets,
            RemoteAnimationTarget[] nonAppTargets) {
        AnimatorSet target = new AnimatorSet();
        boolean activityClosing = taskIsATargetWithMode(appTargets, getTaskId(), MODE_CLOSING);
        PendingAnimation pa = new PendingAnimation(RECENTS_LAUNCH_DURATION);
        createRecentsWindowAnimator(recentsView, taskView, !activityClosing, appTargets,
                wallpaperTargets, nonAppTargets, null /* depthController */, pa);
        target.play(pa.buildAnim());

        // Found a visible recents task that matches the opening app, lets launch the app from there
        if (activityClosing) {
            Animator adjacentAnimation = mFallbackRecentsView
                    .createAdjacentPageAnimForTaskLaunch(taskView);
            adjacentAnimation.setInterpolator(Interpolators.TOUCH_RESPONSE);
            adjacentAnimation.setDuration(RECENTS_LAUNCH_DURATION);
            adjacentAnimation.addListener(resetStateListener());
            target.play(adjacentAnimation);
        }
        return target;
    }

    @Override
    protected void onStart() {
        // Set the alpha to 1 before calling super, as it may get set back to 0 due to
        // onActivityStart callback.
        mFallbackRecentsView.setContentAlpha(1);
        super.onStart();
        mFallbackRecentsView.updateLocusId();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Workaround for b/78520668, explicitly trim memory once UI is hidden
        onTrimMemory(TRIM_MEMORY_UI_HIDDEN);
        mFallbackRecentsView.updateLocusId();
        AccessibilityManagerCompat.sendTestProtocolEventToTest(
                this, LAUNCHER_ACTIVITY_STOPPED_MESSAGE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AccessibilityManagerCompat.sendStateEventToTest(getBaseContext(), OVERVIEW_STATE_ORDINAL);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mStateManager = new StateManager<>(this, RecentsState.BG_LAUNCHER);

        initDeviceProfile();
        setupViews();

        getSystemUiController().updateUiState(SystemUiController.UI_STATE_BASE_WINDOW,
                Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText));
        ACTIVITY_TRACKER.handleCreate(this);
    }

    @Override
    public void onStateSetEnd(RecentsState state) {
        super.onStateSetEnd(state);

        if (state == RecentsState.DEFAULT) {
            AccessibilityManagerCompat.sendStateEventToTest(getBaseContext(),
                    OVERVIEW_STATE_ORDINAL);
        }
    }

    /**
     * Initialize/update the device profile.
     */
    private void initDeviceProfile() {
        mDeviceProfile = createDeviceProfile();
        onDeviceProfileInitiated();
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        // After the transition to home, enable the high-res thumbnail loader if it wasn't enabled
        // as a part of quickstep, so that high-res thumbnails can load the next time we enter
        // overview
        RecentsModel.INSTANCE.get(this).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        RecentsModel.INSTANCE.get(this).onTrimMemory(level);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ACTIVITY_TRACKER.onActivityDestroyed(this);
        mActivityLaunchAnimationRunner = null;
        mSplitSelectStateController.onDestroy();
        mTISBindHelper.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // TODO: Launch the task we came from
        startHome();
    }

    public void startHome() {
        RecentsView recentsView = getOverviewPanel();
        recentsView.switchToScreenshot(() -> recentsView.finishRecentsAnimation(true,
                this::startHomeInternal));
    }

    private void startHomeInternal() {
        LauncherAnimationRunner runner = new LauncherAnimationRunner(
                getMainThreadHandler(), mAnimationToHomeFactory, true);
        ActivityOptions options = ActivityOptions.makeRemoteAnimation(
                new RemoteAnimationAdapter(runner, HOME_APPEAR_DURATION, 0),
                new RemoteTransition(runner.toRemoteTransition(), getIApplicationThread(),
                        "StartHomeFromRecents"));
        startHomeIntentSafely(this, options.toBundle(), TAG);
    }

    private final RemoteAnimationFactory mAnimationToHomeFactory =
            (transit, appTargets, wallpaperTargets, nonAppTargets, result) -> {
                AnimatorPlaybackController controller =
                        getStateManager().createAnimationToNewWorkspace(
                                RecentsState.BG_LAUNCHER, HOME_APPEAR_DURATION);
                controller.dispatchOnStart();

                RemoteAnimationTargets targets = new RemoteAnimationTargets(
                        appTargets, wallpaperTargets, nonAppTargets, MODE_OPENING);
                for (RemoteAnimationTarget app : targets.apps) {
                    new Transaction().setAlpha(app.leash, 1).apply();
                }
                AnimatorSet anim = new AnimatorSet();
                anim.play(controller.getAnimationPlayer());
                anim.setDuration(HOME_APPEAR_DURATION);
                result.setAnimation(anim, RecentsActivity.this,
                        () -> getStateManager().goToState(RecentsState.HOME, false),
                        true /* skipFirstFrame */);
            };

    @Override
    protected void collectStateHandlers(List<StateHandler> out) {
        out.add(new FallbackRecentsStateController(this));
    }

    @Override
    public StateManager<RecentsState> getStateManager() {
        return mStateManager;
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.println(prefix + "Misc:");
        dumpMisc(prefix + "\t", writer);
    }

    @Override
    public AtomicAnimationFactory<RecentsState> createAtomicAnimationFactory() {
        return new RecentsAtomicAnimationFactory<>(this);
    }

    @Override
    public void dispatchDeviceProfileChanged() {
        super.dispatchDeviceProfileChanged();
        Trace.instantForTrack(TRACE_TAG_APP, "RecentsActivity#DeviceProfileChanged",
                getDeviceProfile().toSmallString());
    }

    private AnimatorListenerAdapter resetStateListener() {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mFallbackRecentsView.resetTaskVisuals();
                mStateManager.reapplyState();
            }
        };
    }

    public boolean canStartHomeSafely() {
        OverviewCommandHelper overviewCommandHelper = mTISBindHelper.getOverviewCommandHelper();
        return overviewCommandHelper == null || overviewCommandHelper.canStartHomeSafely();
    }

    @NonNull
    public TISBindHelper getTISBindHelper() {
        return mTISBindHelper;
    }
}
