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
package com.android.launcher3;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_HIDE_BACK_BUTTON;
import static com.android.launcher3.LauncherState.FLAG_HIDE_BACK_BUTTON;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.NO_OFFSET;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.model.data.ItemInfo.NO_MATCHING_ID;
import static com.android.launcher3.util.DisplayController.CHANGE_ACTIVE_SCREEN;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;
import static com.android.quickstep.SysUINavigationMode.Mode.TWO_BUTTONS;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Insets;
import android.hardware.SensorManager;
import android.hardware.devicestate.DeviceStateManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.view.View;
import android.view.WindowInsets;
import android.window.SplashScreen;

import androidx.annotation.Nullable;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.WellbeingModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.proxy.ProxyActivityStarter;
import com.android.launcher3.proxy.StartActivityParams;
import com.android.launcher3.statehandlers.BackButtonAlphaHandler;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.taskbar.LauncherTaskbarUIController;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.uioverrides.RecentsViewStateController;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ObjectWrapper;
import com.android.launcher3.util.UiThreadHelper;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.SysUINavigationMode.NavigationModeChangeListener;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.TouchInteractionService.TISBinder;
import com.android.quickstep.util.LauncherUnfoldAnimationController;
import com.android.quickstep.util.ProxyScreenStatusProvider;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.RemoteFadeOutAnimationListener;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.TISBindHelper;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.unfold.UnfoldTransitionFactory;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.config.UnfoldTransitionConfig;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Extension of Launcher activity to provide quickstep specific functionality
 */
public abstract class BaseQuickstepLauncher extends Launcher
        implements NavigationModeChangeListener {

    private DepthController mDepthController = new DepthController(this);
    private QuickstepTransitionManager mAppTransitionManager;

    /**
     * Reusable command for applying the back button alpha on the background thread.
     */
    public static final UiThreadHelper.AsyncCommand SET_BACK_BUTTON_ALPHA =
            (context, arg1, arg2) -> SystemUiProxy.INSTANCE.get(context).setNavBarButtonAlpha(
                    Float.intBitsToFloat(arg1), arg2 != 0);

    private OverviewActionsView mActionsView;

    private TISBindHelper mTISBindHelper;
    private @Nullable TaskbarManager mTaskbarManager;
    private @Nullable OverviewCommandHelper mOverviewCommandHelper;
    private @Nullable LauncherTaskbarUIController mTaskbarUIController;

    // Will be updated when dragging from taskbar.
    private @Nullable DragOptions mNextWorkspaceDragOptions = null;

    private @Nullable UnfoldTransitionProgressProvider mUnfoldTransitionProgressProvider;
    private @Nullable LauncherUnfoldAnimationController mLauncherUnfoldAnimationController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SysUINavigationMode.INSTANCE.get(this).addModeChangeListener(this);
        addMultiWindowModeChangedListener(mDepthController);
        initUnfoldTransitionProgressProvider();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onPause();
        }

        super.onPause();
    }

    @Override
    public void onDestroy() {
        mAppTransitionManager.onActivityDestroyed();
        if (mUnfoldTransitionProgressProvider != null) {
            mUnfoldTransitionProgressProvider.destroy();
        }

        SysUINavigationMode.INSTANCE.get(this).removeModeChangeListener(this);

        mTISBindHelper.onDestroy();
        if (mTaskbarManager != null) {
            mTaskbarManager.clearActivity(this);
        }

        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onDestroy();
        }

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mOverviewCommandHelper != null) {
            mOverviewCommandHelper.clearPendingCommands();
        }
    }

    public QuickstepTransitionManager getAppTransitionManager() {
        return mAppTransitionManager;
    }

    @Override
    public void onNavigationModeChanged(Mode newMode) {
        getDragLayer().recreateControllers();
        if (mActionsView != null) {
            mActionsView.updateVerticalMargin(newMode);
        }
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
    protected void handleGestureContract(Intent intent) {
        if (FeatureFlags.SEPARATE_RECENTS_ACTIVITY.get()) {
            super.handleGestureContract(intent);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        RecentsModel.INSTANCE.get(this).onTrimMemory(level);
    }

    @Override
    public void onUiChangedWhileSleeping() {
        // Remove the snapshot because the content view may have obvious changes.
        UI_HELPER_EXECUTOR.execute(
                () -> ActivityManagerWrapper.getInstance().invalidateHomeTaskSnapshot(this));
    }

    @Override
    protected void onScreenOff() {
        super.onScreenOff();
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            RecentsView recentsView = getOverviewPanel();
            recentsView.finishRecentsAnimation(true /* toRecents */, null);
        }
    }

    /**
     * {@code LauncherOverlayCallbacks} scroll amount.
     * Indicates transition progress to -1 screen.
     * @param progress From 0 to 1.
     */
    @Override
    public void onScrollChanged(float progress) {
        super.onScrollChanged(progress);
        mDepthController.onOverlayScrollChanged(progress);
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
            StartActivityParams params = new StartActivityParams(this, requestCode);
            params.intentSender = intent;
            params.fillInIntent = fillInIntent;
            params.flagsMask = flagsMask;
            params.flagsValues = flagsValues;
            params.extraFlags = extraFlags;
            params.options = options;
            startActivity(ProxyActivityStarter.getLaunchIntent(this, params));
        } else {
            super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask,
                    flagsValues, extraFlags, options);
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
            StartActivityParams params = new StartActivityParams(this, requestCode);
            params.intent = intent;
            params.options = options;
            startActivity(ProxyActivityStarter.getLaunchIntent(this, params));
        } else {
            super.startActivityForResult(intent, requestCode, options);
        }
    }

    @Override
    protected void onDeferredResumed() {
        super.onDeferredResumed();
        handlePendingActivityRequest();
    }

    @Override
    public void onStateSetEnd(LauncherState state) {
        super.onStateSetEnd(state);
        handlePendingActivityRequest();
    }

    private void handlePendingActivityRequest() {
        if (mPendingActivityRequestCode != -1 && isInState(NORMAL)
                && ((getActivityFlags() & ACTIVITY_STATE_DEFERRED_RESUMED) != 0)) {
            // Remove any active ProxyActivityStarter task and send RESULT_CANCELED to Launcher.
            onActivityResult(mPendingActivityRequestCode, RESULT_CANCELED, null);
            // ProxyActivityStarter is started with clear task to reset the task after which it
            // removes the task itself.
            startActivity(ProxyActivityStarter.getLaunchIntent(this, null));
        }
    }

    @Override
    protected void setupViews() {
        super.setupViews();

        SysUINavigationMode.INSTANCE.get(this).updateMode();
        mActionsView = findViewById(R.id.overview_actions_view);
        RecentsView overviewPanel = (RecentsView) getOverviewPanel();
        SplitSelectStateController controller =
                new SplitSelectStateController(mHandler, SystemUiProxy.INSTANCE.get(this),
                        getStateManager(), getDepthController());
        overviewPanel.init(mActionsView, controller);
        mActionsView.setDp(getDeviceProfile());
        mActionsView.updateVerticalMargin(SysUINavigationMode.getMode(this));

        mAppTransitionManager = new QuickstepTransitionManager(this);
        mAppTransitionManager.registerRemoteAnimations();
        mAppTransitionManager.registerRemoteTransitions();

        mTISBindHelper = new TISBindHelper(this, this::onTISConnected);
    }

    private void onTISConnected(TISBinder binder) {
        mTaskbarManager = binder.getTaskbarManager();
        mTaskbarManager.setActivity(this);
        mOverviewCommandHelper = binder.getOverviewCommandHelper();
    }

    @Override
    public void runOnBindToTouchInteractionService(Runnable r) {
        mTISBindHelper.runOnBindToTouchInteractionService(r);
    }

    private void initUnfoldTransitionProgressProvider() {
        final UnfoldTransitionConfig config = UnfoldTransitionFactory.createConfig(this);
        if (config.isEnabled()) {
            mUnfoldTransitionProgressProvider =
                    UnfoldTransitionFactory.createUnfoldTransitionProgressProvider(
                            this,
                            config,
                            ProxyScreenStatusProvider.INSTANCE,
                            getSystemService(DeviceStateManager.class),
                            getSystemService(SensorManager.class),
                            getMainThreadHandler(),
                            getMainExecutor()
                    );

            mLauncherUnfoldAnimationController = new LauncherUnfoldAnimationController(
                    this,
                    getWindowManager(),
                    mUnfoldTransitionProgressProvider
            );
        }
    }

    public void setTaskbarUIController(LauncherTaskbarUIController taskbarUIController) {
        mTaskbarUIController = taskbarUIController;
    }

    public @Nullable LauncherTaskbarUIController getTaskbarUIController() {
        return mTaskbarUIController;
    }

    public <T extends OverviewActionsView> T getActionsView() {
        return (T) mActionsView;
    }

    @Override
    protected void closeOpenViews(boolean animate) {
        super.closeOpenViews(animate);
        TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY);
    }

    @Override
    protected void collectStateHandlers(List<StateHandler> out) {
        super.collectStateHandlers(out);
        out.add(getDepthController());
        out.add(new RecentsViewStateController(this));
        out.add(new BackButtonAlphaHandler(this));
    }

    public DepthController getDepthController() {
        return mDepthController;
    }

    @Nullable
    public UnfoldTransitionProgressProvider getUnfoldTransitionProgressProvider() {
        return mUnfoldTransitionProgressProvider;
    }

    @Override
    public boolean supportsAdaptiveIconAnimation(View clickedView) {
        return mAppTransitionManager.hasControlRemoteAppTransitionPermission()
                && FeatureFlags.ADAPTIVE_ICON_WINDOW_ANIM.get();
    }

    @Override
    public DragOptions getDefaultWorkspaceDragOptions() {
        if (mNextWorkspaceDragOptions != null) {
            DragOptions options = mNextWorkspaceDragOptions;
            mNextWorkspaceDragOptions = null;
            return options;
        }
        return super.getDefaultWorkspaceDragOptions();
    }

    public void setNextWorkspaceDragOptions(DragOptions dragOptions) {
        mNextWorkspaceDragOptions = dragOptions;
    }

    @Override
    public void useFadeOutAnimationForLauncherStart(CancellationSignal signal) {
        QuickstepTransitionManager appTransitionManager = getAppTransitionManager();
        appTransitionManager.setRemoteAnimationProvider(new RemoteAnimationProvider() {
            @Override
            public AnimatorSet createWindowAnimation(RemoteAnimationTargetCompat[] appTargets,
                    RemoteAnimationTargetCompat[] wallpaperTargets) {

                // On the first call clear the reference.
                signal.cancel();

                ValueAnimator fadeAnimation = ValueAnimator.ofFloat(1, 0);
                fadeAnimation.addUpdateListener(new RemoteFadeOutAnimationListener(appTargets,
                        wallpaperTargets));
                AnimatorSet anim = new AnimatorSet();
                anim.play(fadeAnimation);
                return anim;
            }
        }, signal);
    }

    @Override
    public float[] getNormalOverviewScaleAndOffset() {
        return SysUINavigationMode.getMode(this).hasGestures
                ? new float[] {1, 1} : new float[] {1.1f, NO_OFFSET};
    }

    @Override
    public void onDragLayerHierarchyChanged() {
        onLauncherStateOrFocusChanged();
    }

    @Override
    protected void onActivityFlagsChanged(int changeBits) {
        if ((changeBits
                & (ACTIVITY_STATE_WINDOW_FOCUSED | ACTIVITY_STATE_TRANSITION_ACTIVE)) != 0) {
            onLauncherStateOrFocusChanged();
        }

        if ((changeBits & ACTIVITY_STATE_STARTED) != 0) {
            mDepthController.setActivityStarted(isStarted());
        }

        if ((changeBits & ACTIVITY_STATE_RESUMED) != 0) {
            if (mTaskbarUIController != null) {
                mTaskbarUIController.onLauncherResumedOrPaused(hasBeenResumed());
            }
        }

        super.onActivityFlagsChanged(changeBits);
    }

    public boolean shouldBackButtonBeHidden(LauncherState toState) {
        Mode mode = SysUINavigationMode.getMode(this);
        boolean shouldBackButtonBeHidden = mode.hasGestures
                && toState.hasFlag(FLAG_HIDE_BACK_BUTTON)
                && hasWindowFocus()
                && (getActivityFlags() & ACTIVITY_STATE_TRANSITION_ACTIVE) == 0;
        if (shouldBackButtonBeHidden) {
            // Show the back button if there is a floating view visible.
            shouldBackButtonBeHidden = AbstractFloatingView.getTopOpenViewWithType(this,
                    TYPE_ALL & ~TYPE_HIDE_BACK_BUTTON) == null;
        }
        return shouldBackButtonBeHidden;
    }

    /**
     * Sets the back button visibility based on the current state/window focus.
     */
    private void onLauncherStateOrFocusChanged() {
        boolean shouldBackButtonBeHidden = shouldBackButtonBeHidden(getStateManager().getState());
        if (SysUINavigationMode.getMode(this) == TWO_BUTTONS) {
            UiThreadHelper.setBackButtonAlphaAsync(this, SET_BACK_BUTTON_ALPHA,
                    shouldBackButtonBeHidden ? 0f : 1f, true /* animate */);
        }
        if (getDragLayer() != null) {
            getRootView().setDisallowBackGesture(shouldBackButtonBeHidden);
        }
    }

    @Override
    public void finishBindingItems(IntSet pagesBoundFirst) {
        super.finishBindingItems(pagesBoundFirst);
        // Instantiate and initialize WellbeingModel now that its loading won't interfere with
        // populating workspace.
        // TODO: Find a better place for this
        WellbeingModel.INSTANCE.get(this);
    }

    @Override
    public Stream<SystemShortcut.Factory> getSupportedShortcuts() {
        return Stream.concat(Stream.of(WellbeingModel.SHORTCUT_FACTORY),
                super.getSupportedShortcuts());
    }

    @Override
    public ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
        ActivityOptionsWrapper activityOptions =
                mAppTransitionManager.hasControlRemoteAppTransitionPermission()
                        ? mAppTransitionManager.getActivityLaunchOptions(v)
                        : super.getActivityLaunchOptions(v, item);
        if (mLastTouchUpTime > 0) {
            ActivityOptionsCompat.setLauncherSourceInfo(
                    activityOptions.options, mLastTouchUpTime);
        }
        activityOptions.options.setSplashscreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
        addLaunchCookie(item, activityOptions.options);
        return activityOptions;
    }

    /**
     * Adds a new launch cookie for the activity launch if supported.
     *
     * @param info the item info for the launch
     * @param opts the options to set the launchCookie on.
     */
    public void addLaunchCookie(ItemInfo info, ActivityOptions opts) {
        IBinder launchCookie = getLaunchCookie(info);
        if (launchCookie != null) {
            opts.setLaunchCookie(launchCookie);
        }
    }

    /**
     * Return a new launch cookie for the activity launch if supported.
     *
     * @param info the item info for the launch
     */
    public IBinder getLaunchCookie(ItemInfo info) {
        if (info == null) {
            return null;
        }
        switch (info.container) {
            case LauncherSettings.Favorites.CONTAINER_DESKTOP:
            case LauncherSettings.Favorites.CONTAINER_HOTSEAT:
                // Fall through and continue it's on the workspace (we don't support swiping back
                // to other containers like all apps or the hotseat predictions (which can change)
                break;
            default:
                if (info.container >= 0) {
                    // Also allow swiping to folders
                    break;
                }
                // Reset any existing launch cookies associated with the cookie
                return ObjectWrapper.wrap(NO_MATCHING_ID);
        }
        switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                // Fall through and continue if it's an app, shortcut, or widget
                break;
            default:
                // Reset any existing launch cookies associated with the cookie
                return ObjectWrapper.wrap(NO_MATCHING_ID);
        }
        return ObjectWrapper.wrap(new Integer(info.id));
    }

    public void setHintUserWillBeActive() {
        addActivityFlags(ACTIVITY_STATE_USER_WILL_BE_ACTIVE);
    }

    @Override
    public void onDisplayInfoChanged(Context context, DisplayController.Info info, int flags) {
        super.onDisplayInfoChanged(context, info, flags);
        // When changing screens, force moving to rest state similar to StatefulActivity.onStop, as
        // StatefulActivity isn't called consistently.
        if ((flags & CHANGE_ACTIVE_SCREEN) != 0) {
            getStateManager().moveToRestState();
        }
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        if (mDepthController != null) {
            mDepthController.dump(prefix, writer);
        }
    }

    @Override
    public void updateWindowInsets(WindowInsets.Builder updatedInsetsBuilder,
            WindowInsets oldInsets) {
        // Override the tappable insets to be 0 on the bottom for gesture nav (otherwise taskbar
        // would count towards it). This is used for the bottom protection in All Apps for example.
        if (SysUINavigationMode.getMode(this) == NO_BUTTON) {
            Insets oldTappableInsets = oldInsets.getInsets(WindowInsets.Type.tappableElement());
            Insets newTappableInsets = Insets.of(oldTappableInsets.left, oldTappableInsets.top,
                    oldTappableInsets.right, 0);
            updatedInsetsBuilder.setInsets(WindowInsets.Type.tappableElement(), newTappableInsets);
        }
    }
}
