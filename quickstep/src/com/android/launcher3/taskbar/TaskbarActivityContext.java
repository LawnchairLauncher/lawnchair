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

import static android.os.Trace.TRACE_TAG_APP;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
import static android.window.SplashScreen.SPLASH_SCREEN_STYLE_UNDEFINED;

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_ON_BOARD_POPUP;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_OVERLAY_PROXY;
import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.Flags.removeExcludeFromScreenMagnificationFlagUsage;
import static com.android.launcher3.Utilities.calculateTextHeight;
import static com.android.launcher3.Utilities.isRunningInTestHarness;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarNoRecreate;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarPinning;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_OPEN;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_DRAGGING;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_FULLSCREEN;
import static com.android.launcher3.taskbar.TaskbarStashController.SHOULD_BUBBLES_FOLLOW_DEFAULT_VALUE;
import static com.android.launcher3.testing.shared.ResourceUtils.getBoolByName;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.util.AnimUtils.completeRunnableListCallback;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING;
import static com.android.wm.shell.Flags.enableBubbleBar;
import static com.android.wm.shell.Flags.enableBubbleBarOnPhones;
import static com.android.wm.shell.Flags.enableTinyTaskbar;

import static java.lang.invoke.MethodHandles.Lookup.PROTECTED;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo.Config;
import android.content.pm.LauncherApps;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.IRemoteCallback;
import android.os.Process;
import android.os.Trace;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;
import android.window.DesktopModeFlags.DesktopModeFlag;
import android.window.RemoteTransition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.android.internal.jank.Cuj;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.BubbleTextView.RunningAppState;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.desktop.DesktopAppLaunchTransition;
import com.android.launcher3.desktop.DesktopAppLaunchTransition.AppLaunchType;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.TaskItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.AutohideSuspendFlag;
import com.android.launcher3.taskbar.TaskbarTranslationController.TransitionCallback;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsController;
import com.android.launcher3.taskbar.bubbles.BubbleBarController;
import com.android.launcher3.taskbar.bubbles.BubbleBarPinController;
import com.android.launcher3.taskbar.bubbles.BubbleBarSwipeController;
import com.android.launcher3.taskbar.bubbles.BubbleBarView;
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.taskbar.bubbles.BubbleCreator;
import com.android.launcher3.taskbar.bubbles.BubbleDismissController;
import com.android.launcher3.taskbar.bubbles.BubbleDragController;
import com.android.launcher3.taskbar.bubbles.BubblePinController;
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController.TaskbarHotseatDimensionsProvider;
import com.android.launcher3.taskbar.bubbles.stashing.DeviceProfileDimensionsProviderAdapter;
import com.android.launcher3.taskbar.bubbles.stashing.PersistentBubbleStashController;
import com.android.launcher3.taskbar.bubbles.stashing.TransientBubbleStashController;
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator;
import com.android.launcher3.taskbar.customization.TaskbarSpecsEvaluator;
import com.android.launcher3.taskbar.growth.NudgeController;
import com.android.launcher3.taskbar.navbutton.NearestTouchFrame;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemClickHandler.ItemClickProxy;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ApplicationInfoWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.NavHandle;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SingleTask;
import com.android.quickstep.util.SplitTask;
import com.android.quickstep.views.DesktopTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.animation.ViewRootSync;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.rotation.RotationButtonController;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.unfold.updates.RotationChangeProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;
import com.android.wm.shell.shared.desktopmode.DesktopTaskToFrontReason;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The {@link ActivityContext} with which we inflate Taskbar-related Views. This allows UI elements
 * that are used by both Launcher and Taskbar (such as Folder) to reference a generic
 * ActivityContext and BaseDragLayer instead of the Launcher activity and its DragLayer.
 */
public class TaskbarActivityContext extends BaseTaskbarContext {

    private static final String IME_DRAWS_IME_NAV_BAR_RES_NAME = "config_imeDrawsImeNavBar";

    private static final String TAG = "TaskbarActivityContext";

    private static final String WINDOW_TITLE = "Taskbar";

    protected static final DesktopModeFlag ENABLE_TASKBAR_BEHIND_SHADE = new DesktopModeFlag(
            Flags::enableTaskbarBehindShade, false);

    private final @Nullable Context mNavigationBarPanelContext;

    private final TaskbarDragLayer mDragLayer;
    private final TaskbarControllers mControllers;

    private final WindowManager mWindowManager;
    private final boolean mIsPrimaryDisplay;
    private DeviceProfile mDeviceProfile;
    private WindowManager.LayoutParams mWindowLayoutParams;
    private WindowManager.LayoutParams mLastUpdatedLayoutParams;
    private boolean mIsFullscreen;
    private boolean mIsNotificationShadeExpanded = false;
    // The size we should return to when we call setTaskbarWindowFullscreen(false)
    private int mLastRequestedNonFullscreenSize;
    /**
     * When this is true, the taskbar window size is not updated. Requests to update the window
     * size are stored in {@link #mLastRequestedNonFullscreenSize} and will take effect after
     * bubbles no longer animate and {@link #setTaskbarWindowForAnimatingBubble()} is called.
     */
    private boolean mIsTaskbarSizeFrozenForAnimatingBubble;

    private NavigationMode mNavMode;
    private boolean mImeDrawsImeNavBar;

    private final boolean mIsSafeModeEnabled;
    private final boolean mIsUserSetupComplete;
    private final boolean mIsNavBarForceVisible;
    private final boolean mIsNavBarKidsMode;

    private boolean mIsDestroyed = false;
    // The flag to know if the window is excluded from magnification region computation.
    private boolean mIsExcludeFromMagnificationRegion = false;
    private boolean mAddedWindow = false;

    // The bounds of the taskbar items relative to TaskbarDragLayer
    private final Rect mTransientTaskbarBounds = new Rect();

    private final TaskbarShortcutMenuAccessibilityDelegate mAccessibilityDelegate;

    private DeviceProfile mTransientTaskbarDeviceProfile;

    private DeviceProfile mPersistentTaskbarDeviceProfile;

    private final LauncherPrefs mLauncherPrefs;
    private final SystemUiProxy mSysUiProxy;

    private TaskbarFeatureEvaluator mTaskbarFeatureEvaluator;

    private TaskbarSpecsEvaluator mTaskbarSpecsEvaluator;

    // Snapshot is used to temporarily draw taskbar behind the shade.
    private @Nullable View mTaskbarSnapshotView;
    private @Nullable TaskbarOverlayContext mTaskbarSnapshotOverlay;

    public TaskbarActivityContext(Context windowContext,
            @Nullable Context navigationBarPanelContext, DeviceProfile launcherDp,
            TaskbarNavButtonController buttonController,
            ScopedUnfoldTransitionProgressProvider unfoldTransitionProgressProvider,
            boolean isPrimaryDisplay, SystemUiProxy sysUiProxy) {
        super(windowContext, isPrimaryDisplay);
        mIsPrimaryDisplay = isPrimaryDisplay;
        mNavigationBarPanelContext = navigationBarPanelContext;
        mSysUiProxy = sysUiProxy;
        applyDeviceProfile(launcherDp);
        final Resources resources = getResources();
        mTaskbarFeatureEvaluator = TaskbarFeatureEvaluator.getInstance(this);
        mTaskbarSpecsEvaluator = new TaskbarSpecsEvaluator(
                this,
                mTaskbarFeatureEvaluator,
                mDeviceProfile.inv.numRows,
                mDeviceProfile.inv.numColumns);

        mImeDrawsImeNavBar = getBoolByName(IME_DRAWS_IME_NAV_BAR_RES_NAME, resources, false);
        mIsSafeModeEnabled = TraceHelper.allowIpcs("isSafeMode",
                () -> getPackageManager().isSafeMode());

        // TODO(b/244231596) For shared Taskbar window, update this value in applyDeviceProfile()
        //  instead so to get correct value when recreating the taskbar
        SettingsCache settingsCache = SettingsCache.INSTANCE.get(this);
        mIsUserSetupComplete = settingsCache.getValue(
                Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE), 0);
        mIsNavBarKidsMode = settingsCache.getValue(
                Settings.Secure.getUriFor(Settings.Secure.NAV_BAR_KIDS_MODE), 0);
        mIsNavBarForceVisible = mIsNavBarKidsMode;

        // Get display and corners first, as views might use them in constructor.
        Context c = getApplicationContext();
        mWindowManager = c.getSystemService(WindowManager.class);

        // Inflate views.
        boolean isTransientTaskbar = isTransientTaskbar();
        int taskbarLayout = isTransientTaskbar ? R.layout.transient_taskbar : R.layout.taskbar;
        mDragLayer = (TaskbarDragLayer) mLayoutInflater.inflate(taskbarLayout, null, false);
        TaskbarView taskbarView = mDragLayer.findViewById(R.id.taskbar_view);
        TaskbarScrimView taskbarScrimView = mDragLayer.findViewById(R.id.taskbar_scrim);
        NearestTouchFrame navButtonsView = mDragLayer.findViewById(R.id.navbuttons_view);
        StashedHandleView stashedHandleView = mDragLayer.findViewById(R.id.stashed_handle);
        BubbleBarView bubbleBarView = mDragLayer.findViewById(R.id.taskbar_bubbles);
        FrameLayout bubbleBarContainer = mDragLayer.findViewById(R.id.taskbar_bubbles_container);
        StashedHandleView bubbleHandleView = mDragLayer.findViewById(R.id.stashed_bubble_handle);

        mAccessibilityDelegate = new TaskbarShortcutMenuAccessibilityDelegate(this);

        // If Bubble bar is present, TaskbarControllers depends on it so build it first.
        Optional<BubbleControllers> bubbleControllersOptional = Optional.empty();
        BubbleBarController.onTaskbarRecreated();
        final boolean deviceBubbleBarEnabled = enableBubbleBarOnPhones()
                || (!mDeviceProfile.isPhone && !mDeviceProfile.isVerticalBarLayout());
        if (BubbleBarController.isBubbleBarEnabled()
                && deviceBubbleBarEnabled
                && bubbleBarView != null
                && isPrimaryDisplay
        ) {
            Optional<BubbleStashedHandleViewController> bubbleHandleController = Optional.empty();
            Optional<BubbleBarSwipeController> bubbleBarSwipeController = Optional.empty();
            if (isTransientTaskbar) {
                bubbleHandleController = Optional.of(
                        new BubbleStashedHandleViewController(this, bubbleHandleView));
                bubbleBarSwipeController = Optional.of(new BubbleBarSwipeController(this));
            }
            TaskbarHotseatDimensionsProvider dimensionsProvider =
                    new DeviceProfileDimensionsProviderAdapter(this);
            BubbleStashController bubbleStashController = isTransientTaskbar
                    ? new TransientBubbleStashController(dimensionsProvider, this)
                    : new PersistentBubbleStashController(dimensionsProvider);
            bubbleStashController.setBubbleBarVerticalCenterForHome(
                    launcherDp.getBubbleBarVerticalCenterForHome());
            bubbleControllersOptional = Optional.of(new BubbleControllers(
                    new BubbleBarController(this, bubbleBarView),
                    new BubbleBarViewController(this, bubbleBarView, bubbleBarContainer),
                    bubbleStashController,
                    bubbleHandleController,
                    new BubbleDragController(this, mDragLayer),
                    new BubbleDismissController(this, mDragLayer),
                    new BubbleBarPinController(this, bubbleBarContainer,
                            () -> DisplayController.INSTANCE.get(this).getInfo().currentSize),
                    new BubblePinController(this, bubbleBarContainer,
                            () -> DisplayController.INSTANCE.get(this).getInfo().currentSize),
                    bubbleBarSwipeController,
                    new BubbleCreator(this)
            ));
        }

        // Construct controllers.
        RotationButtonController rotationButtonController = new RotationButtonController(this,
                c.getColor(R.color.floating_rotation_button_light_color),
                c.getColor(R.color.floating_rotation_button_dark_color),
                R.drawable.ic_sysbar_rotate_button_ccw_start_0,
                R.drawable.ic_sysbar_rotate_button_ccw_start_90,
                R.drawable.ic_sysbar_rotate_button_cw_start_0,
                R.drawable.ic_sysbar_rotate_button_cw_start_90,
                () -> getDisplay().getRotation());
        rotationButtonController.setBgExecutor(Executors.UI_HELPER_EXECUTOR);

        mControllers = new TaskbarControllers(this,
                new TaskbarDragController(this),
                buttonController,
                new NavbarButtonsViewController(this, mNavigationBarPanelContext, navButtonsView,
                        getMainThreadHandler()),
                rotationButtonController,
                new TaskbarDragLayerController(this, mDragLayer),
                new TaskbarViewController(this, taskbarView),
                new TaskbarScrimViewController(this, taskbarScrimView),
                new TaskbarUnfoldAnimationController(this, unfoldTransitionProgressProvider,
                        mWindowManager,
                        new RotationChangeProvider(c.getSystemService(DisplayManager.class), this,
                                UI_HELPER_EXECUTOR.getHandler(), getMainThreadHandler())),
                new TaskbarKeyguardController(this),
                new StashedHandleViewController(this, stashedHandleView),
                new TaskbarStashController(this),
                new TaskbarAutohideSuspendController(this),
                new TaskbarPopupController(this),
                new TaskbarForceVisibleImmersiveController(this),
                new TaskbarOverlayController(this, launcherDp),
                new TaskbarAllAppsController(),
                new TaskbarInsetsController(this),
                new VoiceInteractionWindowController(this),
                new TaskbarTranslationController(this),
                new TaskbarSpringOnStashController(this),
                new TaskbarRecentAppsController(this, RecentsModel.INSTANCE.get(this)),
                TaskbarEduTooltipController.newInstance(this),
                new KeyboardQuickSwitchController(),
                new TaskbarPinningController(this),
                bubbleControllersOptional,
                new TaskbarDesktopModeController(this,
                        DesktopVisibilityController.INSTANCE.get(this)),
                new NudgeController(this));

        mLauncherPrefs = LauncherPrefs.get(this);
        onViewCreated();
    }

    /** Updates {@link DeviceProfile} instances for any Taskbar windows. */
    public void updateDeviceProfile(DeviceProfile launcherDp) {
        applyDeviceProfile(launcherDp);
        mControllers.taskbarOverlayController.updateLauncherDeviceProfile(launcherDp);
        mControllers.bubbleControllers.ifPresent(bubbleControllers -> {
            int bubbleBarVerticalCenter = launcherDp.getBubbleBarVerticalCenterForHome();
            bubbleControllers.bubbleStashController
                    .setBubbleBarVerticalCenterForHome(bubbleBarVerticalCenter);
        });
        AbstractFloatingView.closeAllOpenViewsExcept(this, false, TYPE_REBIND_SAFE);
        // Reapply fullscreen to take potential new screen size into account.
        setTaskbarWindowFullscreen(mIsFullscreen);

        dispatchDeviceProfileChanged();
    }

    @Override
    public boolean isTransientTaskbar() {
        return DisplayController.isTransientTaskbar(this) && mIsPrimaryDisplay && !isPhoneMode();
    }

    @Override
    public boolean isPinnedTaskbar() {
        return DisplayController.isPinnedTaskbar(this);
    }

    @Override
    public NavigationMode getNavigationMode() {
        return isPrimaryDisplay() ? DisplayController.getNavigationMode(this)
                : NavigationMode.THREE_BUTTONS;
    }

    @Override
    public boolean isInDesktopMode() {
        return mControllers != null
                && mControllers.taskbarDesktopModeController.isInDesktopMode(getDisplayId());
    }

    @Override
    public boolean showLockedTaskbarOnHome() {
        return DisplayController.showLockedTaskbarOnHome(this);
    }

    @Override
    public boolean showDesktopTaskbarForFreeformDisplay() {
        return DisplayController.showDesktopTaskbarForFreeformDisplay(this);
    }

    @Override
    public boolean isPrimaryDisplay() {
        return mIsPrimaryDisplay;
    }

    /**
     * Copy the original DeviceProfile, match the number of hotseat icons and qsb width and update
     * the icon size
     */
    private void applyDeviceProfile(DeviceProfile originDeviceProfile) {
        Consumer<DeviceProfile> overrideProvider = deviceProfile -> {
            // Taskbar should match the number of icons of hotseat
            deviceProfile.numShownHotseatIcons = originDeviceProfile.numShownHotseatIcons;
            // Same QSB width to have a smooth animation
            deviceProfile.hotseatQsbWidth = originDeviceProfile.hotseatQsbWidth;

            // Update icon size
            deviceProfile.iconSizePx = deviceProfile.taskbarIconSize;
            deviceProfile.updateIconSize(1f, this);
        };
        mDeviceProfile = originDeviceProfile.toBuilder(this)
                .withDimensionsOverride(overrideProvider).build();

        if (isTransientTaskbar()) {
            mTransientTaskbarDeviceProfile = mDeviceProfile;
            mPersistentTaskbarDeviceProfile = mDeviceProfile
                    .toBuilder(this)
                    .withDimensionsOverride(overrideProvider)
                    .setIsTransientTaskbar(false)
                    .build();
        } else {
            mPersistentTaskbarDeviceProfile = mDeviceProfile;
            mTransientTaskbarDeviceProfile = mDeviceProfile
                    .toBuilder(this)
                    .withDimensionsOverride(overrideProvider)
                    .setIsTransientTaskbar(true)
                    .build();
        }
        mNavMode = getNavigationMode();
    }

    /** Called when the visibility of the bubble bar changed. */
    public void bubbleBarVisibilityChanged(boolean isVisible) {
        mControllers.uiController.adjustHotseatForBubbleBar(isVisible);
        mControllers.taskbarViewController.adjustTaskbarForBubbleBar();
    }

    /**
     * Init of taskbar activity context.
     * @param duration If duration is greater than 0, it will be used to create an animation
 *                     for the taskbar create/recreate process.
     */
    public void init(@NonNull TaskbarSharedState sharedState, int duration) {
        mImeDrawsImeNavBar = getBoolByName(IME_DRAWS_IME_NAV_BAR_RES_NAME, getResources(), false);
        mLastRequestedNonFullscreenSize = getDefaultTaskbarWindowSize();
        mWindowLayoutParams = createAllWindowParams();
        mLastUpdatedLayoutParams = new WindowManager.LayoutParams();


        AnimatorSet recreateAnim = null;
        if (duration > 0) {
            recreateAnim = onRecreateAnimation(duration);
        }

        // Initialize controllers after all are constructed.
        mControllers.init(sharedState, recreateAnim);
        // This may not be necessary and can be reverted once we move towards recreating all
        // controllers without re-creating the window
        mControllers.rotationButtonController.onNavigationModeChanged(mNavMode.resValue);
        updateSysuiStateFlags(sharedState.sysuiStateFlags, true /* fromInit */);
        disableNavBarElements(sharedState.disableNavBarDisplayId, sharedState.disableNavBarState1,
                sharedState.disableNavBarState2, false /* animate */);
        onSystemBarAttributesChanged(sharedState.systemBarAttrsDisplayId,
                sharedState.systemBarAttrsBehavior);
        onNavButtonsDarkIntensityChanged(sharedState.navButtonsDarkIntensity);
        onNavigationBarLumaSamplingEnabled(sharedState.mLumaSamplingDisplayId,
                sharedState.mIsLumaSamplingEnabled);
        setWallpaperVisible(sharedState.wallpaperVisible);
        onTransitionModeUpdated(sharedState.barMode, true /* checkBarModes */);

        if (ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
            // W/ the flag not set this entire class gets re-created, which resets the value of
            // mIsDestroyed. We re-use the class for small-screen, so we explicitly have to mark
            // this class as non-destroyed
            mIsDestroyed = false;
        }

        if (!enableTaskbarNoRecreate() && !mAddedWindow) {
            mWindowManager.addView(mDragLayer, mWindowLayoutParams);
            mAddedWindow = true;
        } else {
            notifyUpdateLayoutParams();
        }


        if (recreateAnim != null) {
            recreateAnim.start();
        }
    }

    /**
     * Create AnimatorSet for taskbar create/recreate animation. Further used in init
     */
    public AnimatorSet onRecreateAnimation(int duration) {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(duration);
        return animatorSet;
    }

    /**
     * Called when we want destroy current taskbar with animation as part of recreate process.
     */
    public AnimatorSet onDestroyAnimation(int duration) {
        mIsDestroyed = true;
        AnimatorSet animatorSet = new AnimatorSet();
        mControllers.taskbarViewController.onDestroyAnimation(animatorSet);
        mControllers.taskbarDragLayerController.onDestroyAnimation(animatorSet);
        animatorSet.setInterpolator(LINEAR);
        animatorSet.setDuration(duration);
        return animatorSet;
    }

    /**
     * @return {@code true} if the device profile isn't a large screen profile and we are using a
     * single window for taskbar and navbar.
     */
    public boolean isPhoneMode() {
        return ENABLE_TASKBAR_NAVBAR_UNIFICATION
                && mDeviceProfile.isPhone
                && !mDeviceProfile.isTaskbarPresent;
    }

    /**
     * @return {@code true} if {@link #isPhoneMode()} is true and we're using 3 button-nav
     */
    public boolean isPhoneButtonNavMode() {
        return isPhoneMode() && isThreeButtonNav();
    }

    /**
     * @return {@code true} if {@link #isPhoneMode()} is true and we're using gesture nav
     */
    public boolean isPhoneGestureNavMode() {
        return isPhoneMode() && !isThreeButtonNav();
    }

    /** Returns {@code true} iff a tiny version of taskbar is shown on phone. */
    public boolean isTinyTaskbar() {
        return enableTinyTaskbar() && mDeviceProfile.isPhone && mDeviceProfile.isTaskbarPresent;
    }

    public boolean isBubbleBarOnPhone() {
        return enableBubbleBarOnPhones() && enableBubbleBar() && mDeviceProfile.isPhone;
    }

    /**
     * Returns {@code true} iff bubble bar is enabled (but not necessarily visible /
     * containing bubbles).
     */
    @Override
    public boolean isBubbleBarEnabled() {
        return getBubbleControllers() != null && BubbleBarController.isBubbleBarEnabled();
    }

    private boolean isBubbleBarAnimating() {
        return mControllers
                .bubbleControllers
                .map(controllers -> controllers.bubbleBarViewController.isAnimatingNewBubble())
                .orElse(false);
    }

    /**
     * Returns if software keyboard is docked or input toolbar is placed at the taskbar area
     */
    public boolean isImeDocked() {
        View dragLayer = getDragLayer();
        WindowInsets insets = dragLayer.getRootWindowInsets();
        if (insets == null) {
            return false;
        }

        WindowInsetsCompat insetsCompat =
                WindowInsetsCompat.toWindowInsetsCompat(insets, dragLayer.getRootView());

        if (insetsCompat.isVisible(WindowInsetsCompat.Type.ime())) {
            Insets imeInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.ime());
            return imeInsets.bottom >= getResources().getDimensionPixelSize(
                    R.dimen.floating_ime_inset_height);
        } else {
            return false;
        }
    }

    /**
     * Show Taskbar upon receiving broadcast
     */
    public void showTaskbarFromBroadcast() {
        // If user is in middle of taskbar education handle go to next step of education
        if (mControllers.taskbarEduTooltipController.isBeforeTooltipFeaturesStep()) {
            mControllers.taskbarEduTooltipController.hide();
            mControllers.taskbarEduTooltipController.maybeShowFeaturesEdu();
        }
        mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(false);
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    @Override
    public void dispatchDeviceProfileChanged() {
        super.dispatchDeviceProfileChanged();
        Trace.instantForTrack(TRACE_TAG_APP, "TaskbarActivityContext#DeviceProfileChanged",
                getDeviceProfile().toSmallString());
    }

    @NonNull
    public LauncherPrefs getLauncherPrefs() {
        return mLauncherPrefs;
    }

    /**
     * Returns the View bounds of transient taskbar.
     */
    public Rect getTransientTaskbarBounds() {
        return mTransientTaskbarBounds;
    }

    protected float getCurrentTaskbarWidth() {
        return mControllers.taskbarViewController.getCurrentVisualTaskbarWidth();
    }

    @Override
    public StatsLogManager getStatsLogManager() {
        // Used to mock, can't mock a default interface method directly
        return super.getStatsLogManager();
    }

    /**
     * Creates LayoutParams for adding a view directly to WindowManager as a new window.
     *
     * @param type  The window type to pass to the created WindowManager.LayoutParams.
     * @param title The window title to pass to the created WindowManager.LayoutParams.
     */
    public WindowManager.LayoutParams createDefaultWindowLayoutParams(int type, String title) {
        int windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_SLIPPERY;
        boolean watchOutside = isTransientTaskbar() || isThreeButtonNav();
        if (watchOutside && !isRunningInTestHarness()) {
            windowFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        }
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams(
                MATCH_PARENT,
                mLastRequestedNonFullscreenSize,
                type,
                windowFlags,
                PixelFormat.TRANSLUCENT);
        windowLayoutParams.setTitle(title);
        windowLayoutParams.packageName = getPackageName();
        windowLayoutParams.gravity = Gravity.BOTTOM;
        windowLayoutParams.setFitInsetsTypes(0);
        windowLayoutParams.receiveInsetsIgnoringZOrder = true;
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        windowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        windowLayoutParams.privateFlags =
                WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
        windowLayoutParams.accessibilityTitle = getString(
                isPhoneMode() ? R.string.taskbar_phone_a11y_title : R.string.taskbar_a11y_title);

        return windowLayoutParams;
    }

    /**
     * Creates {@link WindowManager.LayoutParams} for Taskbar, and also sets LP.paramsForRotation
     * for taskbar
     */
    private WindowManager.LayoutParams createAllWindowParams() {
        final int windowType =
                (ENABLE_TASKBAR_NAVBAR_UNIFICATION && mIsPrimaryDisplay) ? TYPE_NAVIGATION_BAR
                        : TYPE_NAVIGATION_BAR_PANEL;
        WindowManager.LayoutParams windowLayoutParams =
                createDefaultWindowLayoutParams(windowType, TaskbarActivityContext.WINDOW_TITLE);

        windowLayoutParams.paramsForRotation = new WindowManager.LayoutParams[4];
        for (int rot = Surface.ROTATION_0; rot <= Surface.ROTATION_270; rot++) {
            WindowManager.LayoutParams lp =
                    createDefaultWindowLayoutParams(windowType,
                            TaskbarActivityContext.WINDOW_TITLE);
            if (isPhoneButtonNavMode()) {
                populatePhoneButtonNavModeWindowLayoutParams(rot, lp);
            }
            windowLayoutParams.paramsForRotation[rot] = lp;
        }

        // Override with current layout params
        WindowManager.LayoutParams currentParams =
                windowLayoutParams.paramsForRotation[getDisplay().getRotation()];
        windowLayoutParams.width = currentParams.width;
        windowLayoutParams.height = currentParams.height;
        windowLayoutParams.gravity = currentParams.gravity;

        return windowLayoutParams;
    }

    /**
     * Update {@link WindowManager.LayoutParams} with values specific to phone and 3 button
     * navigation users
     */
    private void populatePhoneButtonNavModeWindowLayoutParams(int rot,
            WindowManager.LayoutParams lp) {
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.BOTTOM;

        // Override with per-rotation specific values
        switch (rot) {
            case Surface.ROTATION_0, Surface.ROTATION_180 -> {
                lp.height = mLastRequestedNonFullscreenSize;
            }
            case Surface.ROTATION_90 -> {
                lp.width = mLastRequestedNonFullscreenSize;
                lp.gravity = Gravity.END;
            }
            case Surface.ROTATION_270 -> {
                lp.width = mLastRequestedNonFullscreenSize;
                lp.gravity = Gravity.START;
            }
        }
    }

    public void onConfigurationChanged(@Config int configChanges) {
        mControllers.onConfigurationChanged(configChanges);
        if (!mIsUserSetupComplete) {
            setTaskbarWindowSize(getSetupWindowSize());
        }
    }

    public boolean isThreeButtonNav() {
        return mNavMode == NavigationMode.THREE_BUTTONS;
    }

    /** Returns whether taskbar should start align. */
    public boolean shouldStartAlignTaskbar() {
        return isThreeButtonNav() && mDeviceProfile.startAlignTaskbar;
    }

    public boolean isGestureNav() {
        return mNavMode == NavigationMode.NO_BUTTON;
    }

    public boolean imeDrawsImeNavBar() {
        return mImeDrawsImeNavBar;
    }

    public int getCornerRadius() {
        return isPhoneMode() ? 0 : getResources().getDimensionPixelSize(
                R.dimen.persistent_taskbar_corner_radius);
    }

    public WindowManager.LayoutParams getWindowLayoutParams() {
        return mWindowLayoutParams;
    }

    @Override
    public TaskbarDragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public Rect getFolderBoundingBox() {
        return mControllers.taskbarDragLayerController.getFolderBoundingBox();
    }

    @Override
    public TaskbarDragController getDragController() {
        return mControllers.taskbarDragController;
    }

    @Nullable
    public BubbleControllers getBubbleControllers() {
        return mControllers.bubbleControllers.orElse(null);
    }

    @NonNull
    public NavHandle getNavHandle() {
        return mControllers.stashedHandleViewController;
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
        return this::onTaskbarIconClicked;
    }

    /**
     * Change from hotseat/predicted hotseat to taskbar container.
     */
    @Override
    public void applyOverwritesToLogItem(LauncherAtom.ItemInfo.Builder itemInfoBuilder) {
        if (!itemInfoBuilder.hasContainerInfo()) {
            return;
        }
        LauncherAtom.ContainerInfo oldContainer = itemInfoBuilder.getContainerInfo();

        LauncherAtom.TaskBarContainer.Builder taskbarBuilder =
                LauncherAtom.TaskBarContainer.newBuilder();
        if (mControllers.uiController.isInOverviewUi()) {
            taskbarBuilder.setTaskSwitcherContainer(
                    LauncherAtom.TaskSwitcherContainer.newBuilder());
        }

        if (oldContainer.hasPredictedHotseatContainer()) {
            LauncherAtom.PredictedHotseatContainer predictedHotseat =
                    oldContainer.getPredictedHotseatContainer();

            if (predictedHotseat.hasIndex()) {
                taskbarBuilder.setIndex(predictedHotseat.getIndex());
            }
            if (predictedHotseat.hasCardinality()) {
                taskbarBuilder.setCardinality(predictedHotseat.getCardinality());
            }

            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setTaskBarContainer(taskbarBuilder));
        } else if (oldContainer.hasHotseat()) {
            LauncherAtom.HotseatContainer hotseat = oldContainer.getHotseat();

            if (hotseat.hasIndex()) {
                taskbarBuilder.setIndex(hotseat.getIndex());
            }

            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setTaskBarContainer(taskbarBuilder));
        } else if (oldContainer.hasFolder() && oldContainer.getFolder().hasHotseat()) {
            LauncherAtom.FolderContainer.Builder folderBuilder = oldContainer.getFolder()
                    .toBuilder();
            LauncherAtom.HotseatContainer hotseat = folderBuilder.getHotseat();

            if (hotseat.hasIndex()) {
                taskbarBuilder.setIndex(hotseat.getIndex());
            }

            folderBuilder.setTaskbar(taskbarBuilder);
            folderBuilder.clearHotseat();
            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setFolder(folderBuilder));
        } else if (oldContainer.hasAllAppsContainer()) {
            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setAllAppsContainer(oldContainer.getAllAppsContainer().toBuilder()
                            .setTaskbarContainer(taskbarBuilder)));
        } else if (oldContainer.hasPredictionContainer()) {
            itemInfoBuilder.setContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setPredictionContainer(oldContainer.getPredictionContainer().toBuilder()
                            .setTaskbarContainer(taskbarBuilder)));
        }
    }

    @NonNull
    @Override
    public PopupDataProvider getPopupDataProvider() {
        return mControllers.taskbarPopupController.getPopupDataProvider();
    }

    @NonNull
    @Override
    public LauncherBindableItemsContainer getContent() {
        return mControllers.taskbarViewController.getContent();
    }

    @Override
    public ActivityAllAppsContainerView<?> getAppsView() {
        return mControllers.taskbarAllAppsController.getAppsView();
    }

    @Override
    public View.AccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    @Override
    public void onDragStart() {
        setTaskbarWindowFullscreen(true);
    }

    @Override
    public void onDragEnd() {
        onDragEndOrViewRemoved();
    }

    @Override
    public void onPopupVisibilityChanged(boolean isVisible) {
        setTaskbarWindowFocusable(isVisible /* focusable */, false /* imeFocusable */);
    }

    @Override
    public void onSplitScreenMenuButtonClicked() {
        PopupContainerWithArrow popup = PopupContainerWithArrow.getOpen(this);
        if (popup != null) {
            popup.addOnCloseCallback(() -> {
                mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
            });
        }
    }

    @Override
    public ActivityOptionsWrapper makeDefaultActivityOptions(int splashScreenStyle) {
        RunnableList callbacks = new RunnableList();
        ActivityOptions options = ActivityOptions.makeCustomAnimation(this, 0, 0);
        options.setSplashScreenStyle(splashScreenStyle);
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        IRemoteCallback endCallback = completeRunnableListCallback(callbacks, this);
        options.setOnAnimationAbortListener(endCallback);
        options.setOnAnimationFinishedListener(endCallback);

        return new ActivityOptionsWrapper(options, callbacks);
    }

    @Override
    public ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
        return makeDefaultActivityOptions(SPLASH_SCREEN_STYLE_UNDEFINED);
    }

    private ActivityOptionsWrapper getActivityLaunchDesktopOptions() {
        ActivityOptions options = ActivityOptions.makeRemoteTransition(
                createDesktopAppLaunchRemoteTransition(
                        AppLaunchType.LAUNCH, Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_ICON));
        return new ActivityOptionsWrapper(options, new RunnableList());
    }

    /**
     * Sets a new data-source for this taskbar instance
     */
    public void setUIController(@NonNull TaskbarUIController uiController) {
        mControllers.setUiController(uiController);
        if (BubbleBarController.isBubbleBarEnabled() && mControllers.bubbleControllers.isEmpty()) {
            // if the bubble bar was visible in a previous configuration of taskbar and is being
            // recreated now without bubbles, clean up any bubble bar adjustments from hotseat
            bubbleBarVisibilityChanged(/* isVisible= */ false);
        }
    }

    /**
     * Sets the flag indicating setup UI is visible
     */
    public void setSetupUIVisible(boolean isVisible) {
        mControllers.taskbarStashController.setSetupUIVisible(isVisible);
    }

    public void setWallpaperVisible(boolean isVisible) {
        mControllers.navbarButtonsViewController.setWallpaperVisible(isVisible);
    }

    public void checkNavBarModes() {
        mControllers.navbarButtonsViewController.checkNavBarModes();
    }

    public void finishBarAnimations() {
        mControllers.navbarButtonsViewController.finishBarAnimations();
    }

    public void touchAutoDim(boolean reset) {
        mControllers.navbarButtonsViewController.touchAutoDim(reset);
    }

    public void transitionTo(@BarTransitions.TransitionMode int barMode,
            boolean animate) {
        mControllers.navbarButtonsViewController.transitionTo(barMode, animate);
    }

    public void appTransitionPending(boolean pending) {
        mControllers.stashedHandleViewController.setIsAppTransitionPending(pending);
    }

    /**
     * Called when this instance of taskbar is no longer needed
     */
    public void onDestroy() {
        onViewDestroyed();
        mIsDestroyed = true;
        mTaskbarFeatureEvaluator.onDestroy();
        setUIController(TaskbarUIController.DEFAULT);
        mControllers.onDestroy();
        if (!enableTaskbarNoRecreate() && !ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
            mWindowManager.removeViewImmediate(mDragLayer);
            mAddedWindow = false;
        }
        mTaskbarSnapshotView = null;
        mTaskbarSnapshotOverlay = null;
    }

    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    public void updateSysuiStateFlags(@SystemUiStateFlags long systemUiStateFlags,
            boolean fromInit) {
        mControllers.navbarButtonsViewController.updateStateForSysuiFlags(systemUiStateFlags,
                fromInit);
        boolean isShadeVisible = (systemUiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE) != 0;
        onNotificationShadeExpandChanged(isShadeVisible, fromInit || isPhoneMode());
        mControllers.taskbarViewController.setRecentsButtonDisabled(
                mControllers.navbarButtonsViewController.isRecentsDisabled()
                        || isNavBarKidsModeActive());
        mControllers.stashedHandleViewController.setIsHomeButtonDisabled(
                mControllers.navbarButtonsViewController.isHomeDisabled());
        mControllers.stashedHandleViewController.updateStateForSysuiFlags(systemUiStateFlags);
        mControllers.taskbarKeyguardController.updateStateForSysuiFlags(systemUiStateFlags);
        mControllers.taskbarStashController.updateStateForSysuiFlags(
                systemUiStateFlags, fromInit || !isUserSetupComplete());
        mControllers.taskbarScrimViewController.updateStateForSysuiFlags(systemUiStateFlags,
                fromInit);
        mControllers.navButtonController.updateSysuiFlags(systemUiStateFlags);
        mControllers.taskbarForceVisibleImmersiveController.updateSysuiFlags(systemUiStateFlags);
        mControllers.voiceInteractionWindowController.setIsVoiceInteractionWindowVisible(
                (systemUiStateFlags & SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING) != 0, fromInit);
        mControllers.uiController.updateStateForSysuiFlags(systemUiStateFlags);
        mControllers.bubbleControllers.ifPresent(controllers -> {
            controllers.bubbleBarController.updateStateForSysuiFlags(systemUiStateFlags);
            controllers.bubbleStashedHandleViewController.ifPresent(controller ->
                    controller.setIsHomeButtonDisabled(
                            mControllers.navbarButtonsViewController.isHomeDisabled()));
        });
    }

    /**
     * Hides the taskbar icons and background when the notification shade is expanded.
     */
    private void onNotificationShadeExpandChanged(boolean isExpanded, boolean skipAnim) {
        boolean isExpandedUpdated = isExpanded != mIsNotificationShadeExpanded;
        mIsNotificationShadeExpanded = isExpanded;
        // Close all floating views within the Taskbar window to make sure nothing is shown over
        // the notification shade.
        if (isExpanded) {
            AbstractFloatingView.closeAllOpenViewsExcept(this, TYPE_TASKBAR_OVERLAY_PROXY);
        }

        float alpha = isExpanded ? 0 : 1;
        AnimatorSet anim = new AnimatorSet();
        anim.play(mControllers.taskbarViewController.getTaskbarIconAlpha().get(
                TaskbarViewController.ALPHA_INDEX_NOTIFICATION_EXPANDED).animateToValue(alpha));
        anim.play(mControllers.taskbarDragLayerController.getNotificationShadeBgTaskbar()
                .animateToValue(alpha));

        if (isExpandedUpdated) {
            mControllers.bubbleControllers.ifPresent(controllers -> {
                BubbleBarViewController bubbleBarViewController =
                        controllers.bubbleBarViewController;
                anim.play(bubbleBarViewController.getBubbleBarAlpha().get(0).animateToValue(alpha));
                MultiPropertyFactory<View>.MultiProperty handleAlpha =
                        controllers.bubbleStashController.getHandleViewAlpha();
                if (handleAlpha != null) {
                    anim.play(handleAlpha.animateToValue(alpha));
                }
            });
        }
        anim.start();
        if (skipAnim) {
            anim.end();
        }

        updateTaskbarSnapshot(anim, isExpanded);
    }

    private void updateTaskbarSnapshot(AnimatorSet anim, boolean isExpanded) {
        if (!ENABLE_TASKBAR_BEHIND_SHADE.isTrue()) {
            return;
        }
        if (mTaskbarSnapshotView == null) {
            mTaskbarSnapshotView = new View(this);
        }
        if (isExpanded) {
            if (!mTaskbarSnapshotView.isAttachedToWindow()
                    && mDragLayer.isAttachedToWindow()
                    && mDragLayer.isLaidOut()
                    && mTaskbarSnapshotView.getParent() == null) {
                NearestTouchFrame navButtonsView = mDragLayer.findViewById(R.id.navbuttons_view);
                int oldNavButtonsVisibility = navButtonsView.getVisibility();
                navButtonsView.setVisibility(View.INVISIBLE);

                Drawable drawable = new FastBitmapDrawable(BitmapRenderer.createHardwareBitmap(
                        mDragLayer.getWidth(),
                        mDragLayer.getHeight(),
                        mDragLayer::draw));

                navButtonsView.setVisibility(oldNavButtonsVisibility);
                mTaskbarSnapshotView.setBackground(drawable);
                mTaskbarSnapshotView.setAlpha(0f);

                mTaskbarSnapshotView.addOnAttachStateChangeListener(
                        new View.OnAttachStateChangeListener() {
                            @Override
                            public void onViewAttachedToWindow(@NonNull View v) {
                                mTaskbarSnapshotView.removeOnAttachStateChangeListener(this);
                                anim.end();
                                mTaskbarSnapshotView.setAlpha(1f);
                                if (!Utilities.isRunningInTestHarness()) {
                                    ViewRootSync.synchronizeNextDraw(mDragLayer,
                                            mTaskbarSnapshotView,
                                            () -> {});
                                }
                            }

                            @Override
                            public void onViewDetachedFromWindow(@NonNull View v) {}
                        });
                BaseDragLayer.LayoutParams layoutParams = new BaseDragLayer.LayoutParams(
                        mDragLayer.getWidth(), mDragLayer.getHeight());
                layoutParams.gravity = mWindowLayoutParams.gravity;
                layoutParams.ignoreInsets = true;
                mTaskbarSnapshotOverlay = mControllers.taskbarOverlayController.requestWindow();
                mTaskbarSnapshotOverlay.getDragLayer().addView(mTaskbarSnapshotView, layoutParams);
            }
        } else {
            Runnable removeSnapshotView = () -> {
                if (mTaskbarSnapshotOverlay != null) {
                    mTaskbarSnapshotOverlay.getDragLayer().removeView(mTaskbarSnapshotView);
                    mTaskbarSnapshotView = null;
                    mTaskbarSnapshotOverlay = null;
                }
            };
            if (mTaskbarSnapshotView.isAttachedToWindow()) {
                mTaskbarSnapshotView.setAlpha(0f);
                anim.end();
                if (Utilities.isRunningInTestHarness()) {
                    removeSnapshotView.run();
                } else {
                    ViewRootSync.synchronizeNextDraw(mDragLayer, mTaskbarSnapshotView,
                            removeSnapshotView);
                }
            } else {
                removeSnapshotView.run();
            }
        }
    }

    public void onRotationProposal(int rotation, boolean isValid) {
        mControllers.rotationButtonController.onRotationProposal(rotation, isValid);
    }

    public void disableNavBarElements(int displayId, int state1, int state2, boolean animate) {
        if (displayId != getDisplayId()) {
            return;
        }
        mControllers.rotationButtonController.onDisable2FlagChanged(state2);
    }

    public void onSystemBarAttributesChanged(int displayId, int behavior) {
        mControllers.rotationButtonController.onBehaviorChanged(displayId, behavior);
    }

    public void onTransitionModeUpdated(int barMode, boolean checkBarModes) {
        mControllers.navbarButtonsViewController.onTransitionModeUpdated(barMode, checkBarModes);
    }

    public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
        mControllers.navbarButtonsViewController.getTaskbarNavButtonDarkIntensity().updateValue(
                darkIntensity);
    }

    public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {
        mControllers.stashedHandleViewController.onNavigationBarLumaSamplingEnabled(displayId,
                enable);
    }

    /**
     * Called to update a {@link AutohideSuspendFlag} with a new value.
     */
    public void setAutohideSuspendFlag(@AutohideSuspendFlag int flag, boolean newValue) {
        mControllers.taskbarAutohideSuspendController.updateFlag(flag, newValue);
    }

    /**
     * Updates the TaskbarContainer to MATCH_PARENT vs original Taskbar size.
     */
    public void setTaskbarWindowFullscreen(boolean fullscreen) {
        setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_FULLSCREEN, fullscreen);
        mIsFullscreen = fullscreen;
        setTaskbarWindowSize(fullscreen ? MATCH_PARENT : mLastRequestedNonFullscreenSize);
    }

    /**
     * Updates the taskbar window size according to whether bubbles are animating.
     *
     * <p>This method should be called when bubbles start animating and again after the animation is
     * complete.
     */
    public void setTaskbarWindowForAnimatingBubble() {
        if (isBubbleBarAnimating()) {
            // the default window size accounts for the bubble flyout
            setTaskbarWindowSize(getDefaultTaskbarWindowSize());
            mIsTaskbarSizeFrozenForAnimatingBubble = true;
        } else {
            mIsTaskbarSizeFrozenForAnimatingBubble = false;
            setTaskbarWindowSize(
                    mLastRequestedNonFullscreenSize != 0
                            ? mLastRequestedNonFullscreenSize : getDefaultTaskbarWindowSize());
        }
    }

    /**
     * Called when drag ends or when a view is removed from the DragLayer.
     */
    void onDragEndOrViewRemoved() {
        boolean isDragInProgress = mControllers.taskbarDragController.isSystemDragInProgress();

        // Overlay AFVs are in a separate window and do not require Taskbar to be fullscreen.
        if (!isDragInProgress
                && !AbstractFloatingView.hasOpenView(
                this, TYPE_ALL & ~TYPE_TASKBAR_OVERLAY_PROXY)) {
            // Reverts Taskbar window to its original size
            setTaskbarWindowFullscreen(false);
        }

        setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_DRAGGING, isDragInProgress);
    }

    public boolean isTaskbarWindowFullscreen() {
        return mIsFullscreen;
    }

    /**
     * Updates the TaskbarContainer size (pass {@link #getDefaultTaskbarWindowSize()} to reset).
     */
    public void setTaskbarWindowSize(int size) {
        // In landscape phone button nav mode, we should set the task bar width instead of height
        // because this is the only case in which the nav bar is not on the display bottom.
        boolean landscapePhoneButtonNav = isPhoneButtonNavMode() && mDeviceProfile.isLandscape;
        if ((landscapePhoneButtonNav ? mWindowLayoutParams.width : mWindowLayoutParams.height)
                == size || mIsDestroyed) {
            return;
        }
        if (size == MATCH_PARENT) {
            size = mDeviceProfile.heightPx;
        } else {
            mLastRequestedNonFullscreenSize = size;
            if (mIsFullscreen || mIsTaskbarSizeFrozenForAnimatingBubble) {
                // We either still need to be fullscreen or a bubble is still animating, so defer
                // any change to our height until setTaskbarWindowFullscreen(false) is called or
                // setTaskbarWindowForAnimatingBubble() is called after the bubble animation
                // completed. For example, this could happen when dragging from the gesture region,
                // as the drag will cancel the gesture and reset launcher's state, which in turn
                // normally would reset the taskbar window height as well.
                return;
            }
        }
        if (landscapePhoneButtonNav) {
            mWindowLayoutParams.width = size;
            for (int rot = Surface.ROTATION_0; rot <= Surface.ROTATION_270; rot++) {
                mWindowLayoutParams.paramsForRotation[rot].width = size;
            }
        } else {
            mWindowLayoutParams.height = size;
            for (int rot = Surface.ROTATION_0; rot <= Surface.ROTATION_270; rot++) {
                mWindowLayoutParams.paramsForRotation[rot].height = size;
            }
        }
        mControllers.runAfterInit(
                mControllers.taskbarInsetsController
                        ::onTaskbarOrBubblebarWindowHeightOrInsetsChanged);
        notifyUpdateLayoutParams();
    }

    /**
     * Returns the default size (in most cases height, but in 3-button phone mode, width) of the
     * window, including the static corner radii above taskbar.
     */
    public int getDefaultTaskbarWindowSize() {
        Resources resources = getResources();

        if (isPhoneMode()) {
            return isThreeButtonNav() ?
                    resources.getDimensionPixelSize(R.dimen.taskbar_phone_size) :
                    resources.getDimensionPixelSize(R.dimen.taskbar_stashed_size);
        }

        if (!isUserSetupComplete()) {
            return getSetupWindowSize();
        }

        int bubbleBarTop = mControllers.bubbleControllers.map(bubbleControllers ->
                bubbleControllers.bubbleBarViewController.getBubbleBarWithFlyoutMaximumHeight()
        ).orElse(0);
        int taskbarWindowSize;
        boolean shouldTreatAsTransient =
                isTransientTaskbar() || (enableTaskbarPinning() && !isThreeButtonNav());

        int extraHeightForTaskbarTooltips = enableCursorHoverStates()
                ? resources.getDimensionPixelSize(R.dimen.arrow_toast_arrow_height)
                + (resources.getDimensionPixelSize(R.dimen.taskbar_tooltip_vertical_padding) * 2)
                + calculateTextHeight(
                resources.getDimensionPixelSize(R.dimen.arrow_toast_text_size))
                : 0;

        // Return transient taskbar window height when pinning feature is enabled, so taskbar view
        // does not get cut off during pinning animation.
        if (shouldTreatAsTransient) {
            DeviceProfile transientTaskbarDp = mDeviceProfile.toBuilder(this)
                    .setIsTransientTaskbar(true).build();

            taskbarWindowSize = transientTaskbarDp.taskbarHeight
                    + (2 * transientTaskbarDp.taskbarBottomMargin)
                    + Math.max(extraHeightForTaskbarTooltips, resources.getDimensionPixelSize(
                    R.dimen.transient_taskbar_shadow_blur));
            return Math.max(taskbarWindowSize, bubbleBarTop);
        }


        taskbarWindowSize =  mDeviceProfile.taskbarHeight
                + getCornerRadius()
                + extraHeightForTaskbarTooltips;
        return Math.max(taskbarWindowSize, bubbleBarTop);
    }

    public int getSetupWindowSize() {
        return getResources().getDimensionPixelSize(R.dimen.taskbar_suw_frame);
    }

    public DeviceProfile getTransientTaskbarDeviceProfile() {
        return mTransientTaskbarDeviceProfile;
    }

    public DeviceProfile getPersistentTaskbarDeviceProfile() {
        return mPersistentTaskbarDeviceProfile;
    }

    /**
     * Sets whether the taskbar window should be focusable and IME focusable. This won't be IME
     * focusable unless it is also focusable.
     *
     * @param focusable    whether it should be focusable.
     * @param imeFocusable whether it should be IME focusable.
     *
     * @see WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE
     * @see WindowManager.LayoutParams#FLAG_ALT_FOCUSABLE_IM
     */
    public void setTaskbarWindowFocusable(boolean focusable, boolean imeFocusable) {
        if (isPhoneMode()) {
            return;
        }
        if (focusable) {
            mWindowLayoutParams.flags &= ~FLAG_NOT_FOCUSABLE;
            if (imeFocusable) {
                mWindowLayoutParams.flags &= ~FLAG_ALT_FOCUSABLE_IM;
            } else {
                mWindowLayoutParams.flags |= FLAG_ALT_FOCUSABLE_IM;
            }
        } else {
            mWindowLayoutParams.flags |= FLAG_NOT_FOCUSABLE;
            mWindowLayoutParams.flags &= ~FLAG_ALT_FOCUSABLE_IM;
        }
        notifyUpdateLayoutParams();
    }

    /**
     * Applies forcibly show flag to taskbar window iff transient taskbar is unstashed.
     */
    public void applyForciblyShownFlagWhileTransientTaskbarUnstashed(boolean shouldForceShow) {
        if (!isTransientTaskbar() || isPhoneMode()) {
            return;
        }
        if (shouldForceShow) {
            mWindowLayoutParams.forciblyShownTypes |= WindowInsets.Type.navigationBars();
        } else {
            mWindowLayoutParams.forciblyShownTypes &= ~WindowInsets.Type.navigationBars();
        }
        notifyUpdateLayoutParams();
    }

    /**
     * Sets whether the taskbar window should be focusable, as well as IME focusable. If we're now
     * focusable, also move nav buttons to a separate window above IME.
     *
     * @param focusable whether it should be focusable.
     *
     * @see WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE
     */
    public void setTaskbarWindowFocusableForIme(boolean focusable) {
        if (focusable) {
            mControllers.navbarButtonsViewController.moveNavButtonsToNewWindow();
        } else {
            mControllers.navbarButtonsViewController.moveNavButtonsBackToTaskbarWindow();
        }
        setTaskbarWindowFocusable(focusable, true /* imeFocusable */);
    }

    /** Adds the given view to WindowManager with the provided LayoutParams (creates new window). */
    public void addWindowView(View view, WindowManager.LayoutParams windowLayoutParams) {
        if (!view.isAttachedToWindow()) {
            mWindowManager.addView(view, windowLayoutParams);
        }
    }

    /** Removes the given view from WindowManager. See {@link #addWindowView}. */
    public void removeWindowView(View view) {
        if (view.isAttachedToWindow()) {
            mWindowManager.removeViewImmediate(view);
        }
    }

    @Override
    public void startSplitSelection(SplitSelectSource splitSelectSource) {
        mControllers.uiController.startSplitSelection(splitSelectSource);
    }

    protected void onTaskbarIconClicked(View view) {
        TaskbarUIController taskbarUIController = mControllers.uiController;
        RecentsView recents = taskbarUIController.getRecentsView();
        boolean shouldCloseAllOpenViews = true;
        Object tag = view.getTag();

        mControllers.keyboardQuickSwitchController.closeQuickSwitchView(false);

        // TODO: b/316004172, b/343289567: Handle `DesktopTask` and `SplitTask`.
        if (tag instanceof SingleTask singleTask) {
            RemoteTransition remoteTransition =
                    (isInDesktopMode() && canUnminimizeDesktopTask(
                            singleTask.getTask().key.id))
                            ? createDesktopAppLaunchRemoteTransition(AppLaunchType.UNMINIMIZE,
                            Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_ICON)
                            : null;
            if (isInDesktopMode() && mControllers.uiController.isInOverviewUi()) {
                RunnableList runnableList = recents.launchRunningDesktopTaskView();
                // Wrapping it in runnable so we post after DW is ready for the app
                // launch.
                if (runnableList != null) {
                    runnableList.add(() -> UI_HELPER_EXECUTOR.execute(
                            () -> handleGroupTaskLaunch(singleTask, remoteTransition,
                                    isInDesktopMode(),
                                    DesktopTaskToFrontReason.TASKBAR_TAP)));
                }
            } else {
                handleGroupTaskLaunch(singleTask, remoteTransition, isInDesktopMode(),
                        DesktopTaskToFrontReason.TASKBAR_TAP);
            }
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
        } else if (tag instanceof FolderInfo) {
            // Tapping an expandable folder icon on Taskbar
            shouldCloseAllOpenViews = false;
            expandFolder((FolderIcon) view);
        } else if (tag instanceof AppPairInfo api) {
            // Tapping an app pair icon on Taskbar
            if (recents != null && recents.isSplitSelectionActive()) {
                Toast.makeText(this, "Unable to split with an app pair. Select another app.",
                        Toast.LENGTH_SHORT).show();
            } else {
                // Else launch the selected app pair
                launchFromTaskbar(recents, view, api.getContents());
                mControllers.uiController.onTaskbarIconLaunched(api);
                mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
            }
        } else if (tag instanceof TaskItemInfo info) {
            RemoteTransition remoteTransition = canUnminimizeDesktopTask(info.getTaskId())
                    ? createDesktopAppLaunchRemoteTransition(
                            AppLaunchType.UNMINIMIZE, Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_ICON)
                    : null;


            if (isInDesktopMode() && mControllers.uiController.isInOverviewUi()) {
                RunnableList runnableList = recents.launchRunningDesktopTaskView();
                if (runnableList != null) {
                    runnableList.add(() ->
                            // wrapped it in runnable here since we need the post for DW to be
                            // ready. if we don't other DW will be gone and only the launched
                            // task will show.
                            UI_HELPER_EXECUTOR.execute(() ->
                                    SystemUiProxy.INSTANCE.get(this).showDesktopApp(
                                            info.getTaskId(), remoteTransition,
                                            DesktopTaskToFrontReason.TASKBAR_TAP)));
                }
            } else {
                UI_HELPER_EXECUTOR.execute(() ->
                        SystemUiProxy.INSTANCE.get(this).showDesktopApp(
                                info.getTaskId(), remoteTransition,
                                DesktopTaskToFrontReason.TASKBAR_TAP));
            }

            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(
                    /* stash= */ true);
        } else if (tag instanceof WorkspaceItemInfo) {
            // Tapping a launchable icon on Taskbar
            WorkspaceItemInfo info = (WorkspaceItemInfo) tag;
            if (!info.isDisabled() || !ItemClickHandler.handleDisabledItemClicked(info, this)) {
                if (recents != null && recents.isSplitSelectionActive()) {
                    // If we are selecting a second app for split, launch the split tasks
                    taskbarUIController.triggerSecondAppForSplit(info, info.intent, view);
                } else {
                    // Else launch the selected task
                    Intent intent = new Intent(info.getIntent())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        if (mIsSafeModeEnabled
                                && !new ApplicationInfoWrapper(this, intent).isSystem()) {
                            Toast.makeText(this, R.string.safemode_shortcut_error,
                                    Toast.LENGTH_SHORT).show();
                        } else if (info.isPromise()) {
                            TestLogging.recordEvent(
                                    TestProtocol.SEQUENCE_MAIN, "start: taskbarPromiseIcon");
                            intent = ApiWrapper.INSTANCE.get(this).getAppMarketActivityIntent(
                                    info.getTargetPackage(), Process.myUserHandle());
                            startActivity(intent);

                        } else if (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                            TestLogging.recordEvent(
                                    TestProtocol.SEQUENCE_MAIN, "start: taskbarDeepShortcut");
                            String id = info.getDeepShortcutId();
                            String packageName = intent.getPackage();
                            getSystemService(LauncherApps.class)
                                    .startShortcut(packageName, id, null, null, info.user);
                        } else {
                            launchFromTaskbar(recents, view, Collections.singletonList(info));
                        }

                    } catch (NullPointerException
                             | ActivityNotFoundException
                             | SecurityException e) {
                        Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT)
                                .show();
                        Log.e(TAG, "Unable to launch. tag=" + info + " intent=" + intent, e);
                        return;
                    }
                }

                // If the app was launched from a folder, stash the taskbar after it closes
                Folder f = Folder.getOpen(this);
                if (f != null && f.getInfo().id == info.container) {
                    f.addOnFolderStateChangedListener(new Folder.OnFolderStateChangedListener() {
                        @Override
                        public void onFolderStateChanged(int newState) {
                            if (newState == Folder.STATE_CLOSED) {
                                f.removeOnFolderStateChangedListener(this);
                                mControllers.taskbarStashController
                                        .updateAndAnimateTransientTaskbar(true);
                            }
                        }
                    });
                }
                mControllers.uiController.onTaskbarIconLaunched(info);
                mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
            }
        } else if (tag instanceof AppInfo) {
            // Tapping an item in AllApps
            AppInfo info = (AppInfo) tag;
            if (recents != null && recents.isSplitSelectionActive()) {
                // If we are selecting a second app for split, launch the split tasks
                taskbarUIController.triggerSecondAppForSplit(info, info.intent, view);
            } else {
                launchFromTaskbar(recents, view, Collections.singletonList(info));
            }
            mControllers.uiController.onTaskbarIconLaunched(info);
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
        } else if (tag instanceof ItemClickProxy) {
            ((ItemClickProxy) tag).onItemClicked(view);
        } else {
            Log.e(TAG, "Unknown type clicked: " + tag);
        }

        mControllers.taskbarPopupController.maybeCloseMultiInstanceMenu();
        if (shouldCloseAllOpenViews) {
            AbstractFloatingView.closeAllOpenViews(this);
        }
    }

    public void handleGroupTaskLaunch(
            GroupTask task,
            @Nullable RemoteTransition remoteTransition,
            boolean onDesktop,
            DesktopTaskToFrontReason toFrontReason) {
        handleGroupTaskLaunch(task, remoteTransition, onDesktop, toFrontReason,
                /* onStartCallback= */ null, /* onFinishCallback= */ null);
    }

    /**
     * Launches the given GroupTask with the following behavior:
     * - If the GroupTask is a DesktopTask, launch the tasks in that Desktop.
     * - If {@code onDesktop}, bring the given GroupTask to the front.
     * - If the GroupTask is a single task, launch it via startActivityFromRecents.
     * - Otherwise, we assume the GroupTask is a Split pair and launch them together.
     * <p>
     * Given start and/or finish callbacks, they will be run before an after the app launch
     * respectively in cases where we can't use the remote transition, otherwise we will assume that
     * these callbacks are included in the remote transition.
     */
    public void handleGroupTaskLaunch(
            GroupTask task,
            @Nullable RemoteTransition remoteTransition,
            boolean onDesktop,
            DesktopTaskToFrontReason toFrontReason,
            @Nullable Runnable onStartCallback,
            @Nullable Runnable onFinishCallback) {
        if (task instanceof DesktopTask) {
            UI_HELPER_EXECUTOR.execute(() ->
                    SystemUiProxy.INSTANCE.get(this).showDesktopApps(getDisplay().getDisplayId(),
                            remoteTransition));
            return;
        }
        if (onDesktop && task instanceof SingleTask singleTask) {
            boolean useRemoteTransition = canUnminimizeDesktopTask(singleTask.getTask().key.id);
            UI_HELPER_EXECUTOR.execute(() -> {
                if (onStartCallback != null) {
                    onStartCallback.run();
                }
                SystemUiProxy.INSTANCE.get(this).showDesktopApp(singleTask.getTask().key.id,
                        useRemoteTransition ? remoteTransition : null, toFrontReason);
                if (onFinishCallback != null) {
                    onFinishCallback.run();
                }
            });
            return;
        }
        if (task instanceof SingleTask singleTask) {
            UI_HELPER_EXECUTOR.execute(() -> {
                ActivityOptions activityOptions =
                        makeDefaultActivityOptions(SPLASH_SCREEN_STYLE_UNDEFINED).options;
                activityOptions.setRemoteTransition(remoteTransition);

                ActivityManagerWrapper.getInstance().startActivityFromRecents(
                        singleTask.getTask().key, activityOptions);
            });
            return;
        }
        assert task instanceof SplitTask;
        mControllers.uiController.launchSplitTasks((SplitTask) task, remoteTransition);
    }

    /** Returns whether the given task is minimized and can be unminimized. */
    public boolean canUnminimizeDesktopTask(int taskId) {
        BubbleTextView.RunningAppState runningAppState =
                mControllers.taskbarRecentAppsController.getRunningAppState(taskId);
        Log.d(TAG, "Task id=" + taskId + ", Running app state=" + runningAppState);
        return runningAppState == RunningAppState.MINIMIZED
                && DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX.isTrue();
    }

    private RemoteTransition createDesktopAppLaunchRemoteTransition(
            AppLaunchType appLaunchType, @Cuj.CujType int cujType) {
        return new RemoteTransition(
                new DesktopAppLaunchTransition(
                        this,
                        appLaunchType,
                        cujType,
                        getMainExecutor()
                ),
                "TaskbarDesktopAppLaunch");
    }

    /**
     * Runs when the user taps a Taskbar icon in TaskbarActivityContext (Overview or inside an app),
     * and calls the appropriate method to animate and launch.
     */
    private void launchFromTaskbar(@Nullable RecentsView recents, @Nullable View launchingIconView,
            List<? extends ItemInfo> itemInfos) {
        if (isInApp()) {
            launchFromInAppTaskbar(recents, launchingIconView, itemInfos);
        } else {
            launchFromOverviewTaskbar(recents, launchingIconView, itemInfos);
        }
    }

    /**
     * Runs when the user taps a Taskbar icon while inside an app.
     */
    private void launchFromInAppTaskbar(@Nullable RecentsView recents,
            @Nullable View launchingIconView, List<? extends ItemInfo> itemInfos) {
        boolean launchedFromExternalDisplay =
                DesktopExperienceFlags.ENABLE_TASKBAR_CONNECTED_DISPLAYS.isTrue()
                        && !mIsPrimaryDisplay;
        if (recents == null && !launchedFromExternalDisplay) {
            return;
        }

        boolean tappedAppPair = itemInfos.size() == 2;

        if (tappedAppPair) {
            // If the icon is an app pair, the logic gets a bit complicated because we play
            // different animations depending on which app (or app pair) is currently running on
            // screen, so delegate logic to appPairsController.
            recents.getSplitSelectController().getAppPairsController()
                    .handleAppPairLaunchInApp((AppPairIcon) launchingIconView, itemInfos);
        } else {
            // Tapped a single app, nothing complicated here.
            startItemInfoActivity(itemInfos.get(0), null /*foundTask*/);
        }
    }

    /**
     * Run when the user taps a Taskbar icon while in Overview. If the tapped app is currently
     * visible to the user in Overview, or is part of a visible split pair, we expand the TaskView
     * as if the user tapped on it (preserving the split pair). Otherwise, launch it normally
     * (potentially breaking a split pair).
     */
    private void launchFromOverviewTaskbar(@Nullable RecentsView recents,
            @Nullable View launchingIconView, List<? extends ItemInfo> itemInfos) {
        if (recents == null) {
            return;
        }

        boolean isLaunchingAppPair = itemInfos.size() == 2;
        // Convert the list of ItemInfo instances to a list of ComponentKeys
        List<ComponentKey> componentKeys =
                itemInfos.stream().map(ItemInfo::getComponentKey).toList();
        recents.getSplitSelectController().findLastActiveTasksAndRunCallback(
                componentKeys,
                isLaunchingAppPair,
                foundTasks -> {
                    @Nullable Task foundTask = foundTasks[0];
                    if (foundTask != null) {
                        TaskView foundTaskView = recents.getTaskViewByTaskId(foundTask.key.id);
                        if (foundTaskView != null
                                && foundTaskView.isVisibleToUser()
                                && !(foundTaskView instanceof DesktopTaskView)) {
                            TestLogging.recordEvent(
                                    TestProtocol.SEQUENCE_MAIN, "start: taskbarAppIcon");
                            foundTaskView.launchWithAnimation();
                            return;
                        }
                    }

                    if (isLaunchingAppPair) {
                        // Finish recents animation if it's running before launching to ensure
                        // we get both leashes for the animation
                        mControllers.uiController.setSkipNextRecentsAnimEnd();
                        recents.switchToScreenshot(() ->
                                recents.finishRecentsAnimation(true /*toRecents*/,
                                        false /*shouldPip*/,
                                        () -> recents
                                                .getSplitSelectController()
                                                .getAppPairsController()
                                                .launchAppPair((AppPairIcon) launchingIconView,
                                                        -1 /*cuj*/)));
                    } else {
                        if (isInDesktopMode()
                                && mControllers.uiController.isInOverviewUi()) {
                            RunnableList runnableList = recents.launchRunningDesktopTaskView();
                            // Wrapping it in runnable so we post after DW is ready for the app
                            // launch.
                            if (runnableList != null) {
                                runnableList.add(() -> UI_HELPER_EXECUTOR.execute(
                                        () -> startItemInfoActivity(itemInfos.get(0), foundTask)));
                            }
                        } else {
                            startItemInfoActivity(itemInfos.get(0), foundTask);
                        }
                    }
                }
        );
    }

    /**
     * Starts an activity with the information provided by the "info" param. However, if
     * taskInRecents is present, it will prioritize re-launching an existing instance via
     * {@link ActivityManagerWrapper#startActivityFromRecents(int, ActivityOptions)}
     */
    private void startItemInfoActivity(ItemInfo info, @Nullable Task taskInRecents) {
        Intent intent = new Intent(info.getIntent())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: taskbarAppIcon");
            if (!info.user.equals(Process.myUserHandle())) {
                // TODO b/376819104: support Desktop launch animations for apps in managed profiles
                getSystemService(LauncherApps.class).startMainActivity(
                        intent.getComponent(), info.user, intent.getSourceBounds(), null);
                return;
            }
            int displayId = getDisplay() == null ? DEFAULT_DISPLAY : getDisplay().getDisplayId();
            // TODO(b/216683257): Use startActivityForResult for search results that require it.
            if (taskInRecents != null) {
                // Re launch instance from recents
                ActivityOptionsWrapper opts = getActivityLaunchOptions(null, info);
                opts.options.setLaunchDisplayId(displayId);
                if (ActivityManagerWrapper.getInstance()
                        .startActivityFromRecents(taskInRecents.key, opts.options)) {
                    mControllers.uiController.getRecentsView()
                            .addSideTaskLaunchCallback(opts.onEndCallback);
                    return;
                }
            }
            if (isInDesktopMode()
                    && DesktopModeFlags.ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX.isTrue()) {
                launchDesktopApp(intent, info, displayId);
            } else {
                startActivity(intent, null);
            }
        } catch (NullPointerException | ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT)
                    .show();
            Log.e(TAG, "Unable to launch. tag=" + info + " intent=" + intent, e);
        }
    }

    private void launchDesktopApp(Intent intent, ItemInfo info, int displayId) {
        TaskbarRecentAppsController.TaskState taskState =
                mControllers.taskbarRecentAppsController.getDesktopItemState(info);
        RunningAppState appState = taskState.getRunningAppState();
        if (appState == RunningAppState.RUNNING || appState == RunningAppState.MINIMIZED) {
            // We only need a custom animation (a RemoteTransition) if the task is minimized - if
            // it's already visible it will just be brought forward.
            RemoteTransition remoteTransition = (appState == RunningAppState.MINIMIZED)
                    ? createDesktopAppLaunchRemoteTransition(
                            AppLaunchType.UNMINIMIZE, Cuj.CUJ_DESKTOP_MODE_APP_LAUNCH_FROM_ICON)
                    : null;
            UI_HELPER_EXECUTOR.execute(() ->
                    SystemUiProxy.INSTANCE.get(this).showDesktopApp(taskState.getTaskId(),
                            remoteTransition, DesktopTaskToFrontReason.TASKBAR_TAP));
            return;
        }
        // There is no task associated with this launch - launch a new task through an intent
        ActivityOptionsWrapper opts = getActivityLaunchDesktopOptions();
        if (DesktopModeFlags.ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX.isTrue()) {
            mSysUiProxy.startLaunchIntentTransition(intent, opts.options.toBundle(), displayId);
        } else {
            startActivity(intent, opts.options.toBundle());
        }
    }

    /** Expands a folder icon when it is clicked */
    private void expandFolder(FolderIcon folderIcon) {
        Folder folder = folderIcon.getFolder();

        folder.setPriorityOnFolderStateChangedListener(
                new Folder.OnFolderStateChangedListener() {
                    @Override
                    public void onFolderStateChanged(int newState) {
                        if (newState == Folder.STATE_OPEN) {
                            setTaskbarWindowFocusableForIme(true);
                        } else if (newState == Folder.STATE_CLOSED) {
                            // Defer by a frame to ensure we're no longer fullscreen and thus
                            // won't jump.
                            getDragLayer().post(() -> setTaskbarWindowFocusableForIme(false));
                            folder.setPriorityOnFolderStateChangedListener(null);
                        }
                    }
                });

        setTaskbarWindowFullscreen(true);

        getDragLayer().post(() -> {
            folder.animateOpen();
            getStatsLogManager().logger().withItemInfo(folder.mInfo).log(LAUNCHER_FOLDER_OPEN);

            folder.mapOverItems((itemInfo, itemView) -> {
                mControllers.taskbarViewController
                        .setClickAndLongClickListenersForIcon(itemView);
                // To play haptic when dragging, like other Taskbar items do.
                itemView.setHapticFeedbackEnabled(true);
                return false;
            });

            // Close any open taskbar tooltips.
            if (AbstractFloatingView.hasOpenView(this, TYPE_ON_BOARD_POPUP)) {
                AbstractFloatingView.getOpenView(this, TYPE_ON_BOARD_POPUP)
                        .close(/* animate= */ false);
            }
        });
    }

    /**
     * Returns whether the taskbar is currently visually stashed.
     */
    public boolean isTaskbarStashed() {
        return mControllers.taskbarStashController.isStashed();
    }

    /**
     * Called when we want to unstash taskbar when user performs swipes up gesture.
     *
     * @param delayTaskbarBackground whether we will delay the taskbar background animation
     */
    public void onSwipeToUnstashTaskbar(boolean delayTaskbarBackground) {
        mControllers.uiController.onSwipeToUnstashTaskbar();

        boolean wasStashed = mControllers.taskbarStashController.isStashed();
        mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(/* stash= */ false,
                SHOULD_BUBBLES_FOLLOW_DEFAULT_VALUE, delayTaskbarBackground);
        boolean isStashed = mControllers.taskbarStashController.isStashed();
        if (isStashed != wasStashed) {
            VibratorWrapper.INSTANCE.get(this).vibrateForTaskbarUnstash();
        }
        mControllers.taskbarEduTooltipController.hide();
    }

    /** Returns {@code true} if Taskbar All Apps is open. */
    public boolean isTaskbarAllAppsOpen() {
        return mControllers.taskbarAllAppsController.isOpen();
    }

    /** Toggles the Taskbar's stash state. */
    public void toggleTaskbarStash() {
        mControllers.taskbarStashController.toggleTaskbarStash();
    }

    /**
     * Plays the taskbar background alpha animation if one is not currently playing.
     */
    public void playTaskbarBackgroundAlphaAnimation() {
        mControllers.taskbarStashController.playTaskbarBackgroundAlphaAnimation();
    }

    /**
     * Called to start the taskbar translation spring to its settled translation (0).
     */
    public void startTranslationSpring() {
        mControllers.taskbarTranslationController.startSpring();
    }

    /**
     * Returns a callback to help monitor the swipe gesture.
     */
    public TransitionCallback getTranslationCallbacks() {
        return mControllers.taskbarTranslationController.getTransitionCallback();
    }

    /**
     * Called when a transient Autohide flag suspend status changes.
     */
    public void onTransientAutohideSuspendFlagChanged(boolean isSuspended) {
        mControllers.taskbarStashController.updateTaskbarTimeout(isSuspended);
    }

    /**
     * Called when we detect a motion down or up/cancel in the nav region while stashed.
     *
     * @param animateForward Whether to animate towards the unstashed hint state or back to stashed.
     */
    public void startTaskbarUnstashHint(boolean animateForward) {
        mControllers.taskbarStashController.startUnstashHint(animateForward);
    }

    /**
     * Enables the auto timeout for taskbar stashing. This method should only be used for taskbar
     * testing.
     */
    @VisibleForTesting
    public void enableBlockingTimeoutDuringTests(boolean enableBlockingTimeout) {
        mControllers.taskbarStashController.enableBlockingTimeoutDuringTests(enableBlockingTimeout);
    }

    /**
     * Unstashes the Taskbar if it is stashed.
     */
    @VisibleForTesting
    public void unstashTaskbarIfStashed() {
        if (isTransientTaskbar()) {
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(false);
        }
    }

    /** Unstashes the Bubble Bar if it is stashed. */
    @VisibleForTesting
    public void unstashBubbleBarIfStashed() {
        mControllers.bubbleControllers.ifPresent(bubbleControllers -> {
            if (bubbleControllers.bubbleStashController.isStashed()) {
                bubbleControllers.bubbleStashController.showBubbleBar(false);
            }
        });
    }

    public boolean isUserSetupComplete() {
        return mIsUserSetupComplete;
    }

    public boolean isNavBarKidsModeActive() {
        return mIsNavBarKidsMode && isThreeButtonNav();
    }

    @VisibleForTesting(otherwise = PROTECTED)
    public boolean isNavBarForceVisible() {
        return mIsNavBarForceVisible;
    }

    /**
     * Displays a single frame of the Launcher start from SUW animation.
     *
     * This animation is a combination of the Launcher resume animation, which animates the hotseat
     * icons into position, the Taskbar unstash to hotseat animation, which animates the Taskbar
     * stash bar into the hotseat icons, and an override to prevent showing the Taskbar all apps
     * button.
     *
     * This should be used to run a Taskbar unstash to hotseat animation whose progress matches a
     * swipe progress.
     *
     * @param duration a placeholder duration to be used to ensure all full-length
     *                 sub-animations are properly coordinated. This duration should not actually
     *                 be used since this animation tracks a swipe progress.
     */
    protected AnimatorPlaybackController createLauncherStartFromSuwAnim(int duration) {
        AnimatorSet fullAnimation = new AnimatorSet();
        fullAnimation.setDuration(duration);

        TaskbarUIController uiController = mControllers.uiController;
        if (uiController instanceof LauncherTaskbarUIController) {
            ((LauncherTaskbarUIController) uiController).addLauncherVisibilityChangedAnimation(
                    fullAnimation, duration);
        }
        mControllers.taskbarStashController.addUnstashToHotseatAnimationFromSuw(fullAnimation,
                duration);

        View allAppsButton = mControllers.taskbarViewController.getAllAppsButtonView();
        if (!FeatureFlags.enableAllAppsButtonInHotseat()) {
            ValueAnimator alphaOverride = ValueAnimator.ofFloat(0, 1);
            alphaOverride.setDuration(duration);
            alphaOverride.addUpdateListener(a -> {
                // Override the alpha updates in the icon alignment animation.
                allAppsButton.setAlpha(0);
            });
            alphaOverride.addListener(AnimatorListeners.forSuccessCallback(
                    () -> allAppsButton.setAlpha(1f)));
            fullAnimation.play(alphaOverride);
        }

        return AnimatorPlaybackController.wrap(fullAnimation, duration);
    }

    /**
     * Called when we determine the touchable region.
     *
     * @param exclude {@code true} then the magnification region computation will omit the window.
     */
    public void excludeFromMagnificationRegion(boolean exclude) {
        if (mIsExcludeFromMagnificationRegion == exclude || isPhoneMode()) {
            return;
        }

        if (removeExcludeFromScreenMagnificationFlagUsage()) {
            return;
        }

        mIsExcludeFromMagnificationRegion = exclude;
        if (exclude) {
            mWindowLayoutParams.privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_EXCLUDE_FROM_SCREEN_MAGNIFICATION;
        } else {
            mWindowLayoutParams.privateFlags &=
                    ~WindowManager.LayoutParams.PRIVATE_FLAG_EXCLUDE_FROM_SCREEN_MAGNIFICATION;
        }
        notifyUpdateLayoutParams();
    }

    void notifyUpdateLayoutParams() {
        if (mDragLayer.isAttachedToWindow()) {
            // Copy the current windowLayoutParams to mLastUpdatedLayoutParams and compare the diff.
            // If there is no change, we will skip the call to updateViewLayout.
            int changes = mLastUpdatedLayoutParams.copyFrom(mWindowLayoutParams);
            if (changes == 0) {
                return;
            }
            if (enableTaskbarNoRecreate()) {
                mWindowManager.updateViewLayout(mDragLayer.getRootView(), mWindowLayoutParams);
            } else {
                mWindowManager.updateViewLayout(mDragLayer, mWindowLayoutParams);
            }
        }
    }

    public void showPopupMenuForIcon(BubbleTextView btv) {
        setTaskbarWindowFullscreen(true);
        btv.post(() -> mControllers.taskbarPopupController.showForIcon(btv));
    }

    public void launchKeyboardFocusedTask() {
        mControllers.uiController.launchKeyboardFocusedTask();
    }

    public boolean isInApp() {
        return mControllers.taskbarStashController.isInApp();
    }

    public boolean isInOverview() {
        return mControllers.taskbarStashController.isInOverview();
    }

    public boolean isInStashedLauncherState() {
        return mControllers.taskbarStashController.isInStashedLauncherState();
    }

    public TaskbarFeatureEvaluator getTaskbarFeatureEvaluator() {
        return mTaskbarFeatureEvaluator;
    }

    public TaskbarSpecsEvaluator getTaskbarSpecsEvaluator() {
        return mTaskbarSpecsEvaluator;
    }

    protected void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarActivityContext:");

        pw.println(String.format(
                "%s\tmNavMode=%s", prefix, mNavMode));
        pw.println(String.format(
                "%s\tmImeDrawsImeNavBar=%b", prefix, mImeDrawsImeNavBar));
        pw.println(String.format(
                "%s\tmIsUserSetupComplete=%b", prefix, mIsUserSetupComplete));
        pw.println(String.format(
                "%s\tmWindowLayoutParams.height=%dpx", prefix, mWindowLayoutParams.height));
        mControllers.dumpLogs(prefix + "\t", pw);
        mDeviceProfile.dump(this, prefix, pw);
    }

    @VisibleForTesting
    public int getTaskbarAllAppsTopPadding() {
        return mControllers.taskbarAllAppsController.getTaskbarAllAppsTopPadding();
    }

    @VisibleForTesting
    public int getTaskbarAllAppsScroll() {
        return mControllers.taskbarAllAppsController.getTaskbarAllAppsScroll();
    }

    @VisibleForTesting
    public float getStashedTaskbarScale() {
        return mControllers.stashedHandleViewController.getStashedHandleHintScale().value;
    }

    /** Closes the KeyboardQuickSwitchView without an animation if open. */
    public void closeKeyboardQuickSwitchView() {
        mControllers.keyboardQuickSwitchController.closeQuickSwitchView(false);
    }

    boolean isIconAlignedWithHotseat() {
        return mControllers.uiController.isIconAlignedWithHotseat();
    }

    // TODO(b/395061396): Remove `otherwise` when overview in widow is enabled.
    @VisibleForTesting(otherwise = PACKAGE_PRIVATE)
    public TaskbarControllers getControllers() {
        return mControllers;
    }
}
