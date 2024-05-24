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
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
import static android.window.SplashScreen.SPLASH_SCREEN_STYLE_UNDEFINED;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_OVERLAY_PROXY;
import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.Utilities.calculateTextHeight;
import static com.android.launcher3.Utilities.isRunningInTestHarness;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarNoRecreate;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarPinning;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_OPEN;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_DRAGGING;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_FULLSCREEN;
import static com.android.launcher3.testing.shared.ResourceUtils.getBoolByName;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.util.AnimUtils.completeRunnableListCallback;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING;
import static com.android.window.flags.Flags.enableDesktopWindowingMode;
import static com.android.window.flags.Flags.enableDesktopWindowingTaskbarRunningApps;
import static com.android.wm.shell.Flags.enableTinyTaskbar;

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
import android.hardware.display.DisplayManager;
import android.os.IRemoteCallback;
import android.os.Process;
import android.os.Trace;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.RoundedCorner;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.AutohideSuspendFlag;
import com.android.launcher3.taskbar.TaskbarTranslationController.TransitionCallback;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsController;
import com.android.launcher3.taskbar.bubbles.BubbleBarController;
import com.android.launcher3.taskbar.bubbles.BubbleBarPinController;
import com.android.launcher3.taskbar.bubbles.BubbleBarView;
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.taskbar.bubbles.BubbleDismissController;
import com.android.launcher3.taskbar.bubbles.BubbleDragController;
import com.android.launcher3.taskbar.bubbles.BubblePinController;
import com.android.launcher3.taskbar.bubbles.BubbleStashController;
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController;
import com.android.launcher3.taskbar.navbutton.NearestTouchFrame;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemClickHandler.ItemClickProxy;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.util.ViewCache;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.LauncherActivityInterface;
import com.android.quickstep.NavHandle;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.rotation.RotationButtonController;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.unfold.updates.RotationChangeProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

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

    private final @Nullable Context mNavigationBarPanelContext;

    private final TaskbarDragLayer mDragLayer;
    private final TaskbarControllers mControllers;

    private final WindowManager mWindowManager;
    private final @Nullable RoundedCorner mLeftCorner, mRightCorner;
    private DeviceProfile mDeviceProfile;
    private WindowManager.LayoutParams mWindowLayoutParams;
    private boolean mIsFullscreen;
    // The size we should return to when we call setTaskbarWindowFullscreen(false)
    private int mLastRequestedNonFullscreenSize;

    private NavigationMode mNavMode;
    private boolean mImeDrawsImeNavBar;
    private final ViewCache mViewCache = new ViewCache();

    private final boolean mIsSafeModeEnabled;
    private final boolean mIsUserSetupComplete;
    private final boolean mIsNavBarForceVisible;
    private final boolean mIsNavBarKidsMode;

    private boolean mIsDestroyed = false;
    // The flag to know if the window is excluded from magnification region computation.
    private boolean mIsExcludeFromMagnificationRegion = false;
    private boolean mBindingItems = false;
    private boolean mAddedWindow = false;

    // The bounds of the taskbar items relative to TaskbarDragLayer
    private final Rect mTransientTaskbarBounds = new Rect();

    private final TaskbarShortcutMenuAccessibilityDelegate mAccessibilityDelegate;

    private DeviceProfile mTransientTaskbarDeviceProfile;

    private DeviceProfile mPersistentTaskbarDeviceProfile;

    private final LauncherPrefs mLauncherPrefs;

    public TaskbarActivityContext(Context windowContext,
            @Nullable Context navigationBarPanelContext, DeviceProfile launcherDp,
            TaskbarNavButtonController buttonController, ScopedUnfoldTransitionProgressProvider
            unfoldTransitionProgressProvider) {
        super(windowContext);

        mNavigationBarPanelContext = navigationBarPanelContext;
        applyDeviceProfile(launcherDp);
        final Resources resources = getResources();

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
        Display display = windowContext.getDisplay();
        Context c = getApplicationContext();
        mWindowManager = c.getSystemService(WindowManager.class);

        boolean phoneMode = isPhoneMode();
        mLeftCorner = phoneMode
                ? null
                : display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
        mRightCorner = phoneMode
                ? null
                : display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);

        // Inflate views.
        int taskbarLayout = DisplayController.isTransientTaskbar(this) && !phoneMode
                ? R.layout.transient_taskbar
                : R.layout.taskbar;
        mDragLayer = (TaskbarDragLayer) mLayoutInflater.inflate(taskbarLayout, null, false);
        TaskbarView taskbarView = mDragLayer.findViewById(R.id.taskbar_view);
        TaskbarScrimView taskbarScrimView = mDragLayer.findViewById(R.id.taskbar_scrim);
        NearestTouchFrame navButtonsView = mDragLayer.findViewById(R.id.navbuttons_view);
        StashedHandleView stashedHandleView = mDragLayer.findViewById(R.id.stashed_handle);
        BubbleBarView bubbleBarView = mDragLayer.findViewById(R.id.taskbar_bubbles);
        StashedHandleView bubbleHandleView = mDragLayer.findViewById(R.id.stashed_bubble_handle);

        mAccessibilityDelegate = new TaskbarShortcutMenuAccessibilityDelegate(this);

        // If Bubble bar is present, TaskbarControllers depends on it so build it first.
        Optional<BubbleControllers> bubbleControllersOptional = Optional.empty();
        BubbleBarController.onTaskbarRecreated();
        if (BubbleBarController.isBubbleBarEnabled() && bubbleBarView != null) {
            bubbleControllersOptional = Optional.of(new BubbleControllers(
                    new BubbleBarController(this, bubbleBarView),
                    new BubbleBarViewController(this, bubbleBarView),
                    new BubbleStashController(this),
                    new BubbleStashedHandleViewController(this, bubbleHandleView),
                    new BubbleDragController(this),
                    new BubbleDismissController(this, mDragLayer),
                    new BubbleBarPinController(this, mDragLayer,
                            () -> DisplayController.INSTANCE.get(this).getInfo().currentSize),
                    new BubblePinController(this, mDragLayer,
                            () -> DisplayController.INSTANCE.get(this).getInfo().currentSize)
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
                new NavbarButtonsViewController(this, mNavigationBarPanelContext, navButtonsView),
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
                createTaskbarRecentAppsController(),
                TaskbarEduTooltipController.newInstance(this),
                new KeyboardQuickSwitchController(),
                new TaskbarPinningController(this, () ->
                        DisplayController.INSTANCE.get(this).getInfo().isInDesktopMode()),
                bubbleControllersOptional);

        mLauncherPrefs = LauncherPrefs.get(this);
    }

    private TaskbarRecentAppsController createTaskbarRecentAppsController() {
        // TODO(b/335401172): unify DesktopMode checks in Launcher
        if (enableDesktopWindowingMode() && enableDesktopWindowingTaskbarRunningApps()) {
            return new DesktopTaskbarRunningAppsController(
                    RecentsModel.INSTANCE.get(this),
                    LauncherActivityInterface.INSTANCE::getDesktopVisibilityController);
        }
        return TaskbarRecentAppsController.DEFAULT;
    }

    /** Updates {@link DeviceProfile} instances for any Taskbar windows. */
    public void updateDeviceProfile(DeviceProfile launcherDp) {
        applyDeviceProfile(launcherDp);

        mControllers.taskbarOverlayController.updateLauncherDeviceProfile(launcherDp);
        AbstractFloatingView.closeAllOpenViewsExcept(this, false, TYPE_REBIND_SAFE);
        // Reapply fullscreen to take potential new screen size into account.
        setTaskbarWindowFullscreen(mIsFullscreen);

        dispatchDeviceProfileChanged();
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

        if (DisplayController.isTransientTaskbar(this)) {
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
        mNavMode = DisplayController.getNavigationMode(this);
    }

    /** Called when the visibility of the bubble bar changed. */
    public void bubbleBarVisibilityChanged(boolean isVisible) {
        mControllers.uiController.adjustHotseatForBubbleBar(isVisible);
        mControllers.taskbarViewController.resetIconAlignmentController();
    }

    public void init(@NonNull TaskbarSharedState sharedState) {
        mImeDrawsImeNavBar = getBoolByName(IME_DRAWS_IME_NAV_BAR_RES_NAME, getResources(), false);
        mLastRequestedNonFullscreenSize = getDefaultTaskbarWindowSize();
        mWindowLayoutParams = createAllWindowParams();

        // Initialize controllers after all are constructed.
        mControllers.init(sharedState);
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
        mControllers.taskbarStashController.showTaskbarFromBroadcast();
    }

    /** Toggles Taskbar All Apps overlay. */
    public void toggleAllApps() {
        mControllers.taskbarAllAppsController.toggle();
    }

    /** Toggles Taskbar All Apps overlay with keyboard ready for search. */
    public void toggleAllAppsSearch() {
        mControllers.taskbarAllAppsController.toggleSearch();
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
                | WindowManager.LayoutParams.FLAG_SLIPPERY
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
        if (DisplayController.isTransientTaskbar(this) && !isRunningInTestHarness()) {
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
                ENABLE_TASKBAR_NAVBAR_UNIFICATION ? TYPE_NAVIGATION_BAR : TYPE_NAVIGATION_BAR_PANEL;
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

    public boolean isGestureNav() {
        return mNavMode == NavigationMode.NO_BUTTON;
    }

    public boolean imeDrawsImeNavBar() {
        return mImeDrawsImeNavBar;
    }

    public int getLeftCornerRadius() {
        return mLeftCorner == null ? 0 : mLeftCorner.getRadius();
    }

    public int getRightCornerRadius() {
        return mRightCorner == null ? 0 : mRightCorner.getRadius();
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
    public ViewCache getViewCache() {
        return mViewCache;
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

    @Override
    public DotInfo getDotInfoForItem(ItemInfo info) {
        return getPopupDataProvider().getDotInfoForItem(info);
    }

    @NonNull
    @Override
    public PopupDataProvider getPopupDataProvider() {
        return mControllers.taskbarPopupController.getPopupDataProvider();
    }

    @Override
    public View.AccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    @Override
    public boolean isBindingItems() {
        return mBindingItems;
    }

    public void setBindingItems(boolean bindingItems) {
        mBindingItems = bindingItems;
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
        setTaskbarWindowFocusable(isVisible);
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
        IRemoteCallback endCallback = completeRunnableListCallback(callbacks);
        options.setOnAnimationAbortListener(endCallback);
        options.setOnAnimationFinishedListener(endCallback);

        return new ActivityOptionsWrapper(options, callbacks);
    }

    @Override
    public ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
        return makeDefaultActivityOptions(SPLASH_SCREEN_STYLE_UNDEFINED);
    }

    /**
     * Sets a new data-source for this taskbar instance
     */
    public void setUIController(@NonNull TaskbarUIController uiController) {
        mControllers.setUiController(uiController);
    }

    /**
     * Sets the flag indicating setup UI is visible
     */
    public void setSetupUIVisible(boolean isVisible) {
        mControllers.taskbarStashController.setSetupUIVisible(isVisible);
    }

    /**
     * Called when this instance of taskbar is no longer needed
     */
    public void onDestroy() {
        mIsDestroyed = true;
        setUIController(TaskbarUIController.DEFAULT);
        mControllers.onDestroy();
        if (!enableTaskbarNoRecreate() && !ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
            mWindowManager.removeViewImmediate(mDragLayer);
            mAddedWindow = false;
        }
    }

    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    public void updateSysuiStateFlags(@SystemUiStateFlags long systemUiStateFlags,
            boolean fromInit) {
        mControllers.navbarButtonsViewController.updateStateForSysuiFlags(systemUiStateFlags,
                fromInit);
        boolean isShadeVisible = (systemUiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE) != 0;
        onNotificationShadeExpandChanged(isShadeVisible, fromInit);
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
            controllers.bubbleStashedHandleViewController.setIsHomeButtonDisabled(
                    mControllers.navbarButtonsViewController.isHomeDisabled());
        });
    }

    /**
     * Hides the taskbar icons and background when the notication shade is expanded.
     */
    private void onNotificationShadeExpandChanged(boolean isExpanded, boolean skipAnim) {
        float alpha = isExpanded ? 0 : 1;
        AnimatorSet anim = new AnimatorSet();
        anim.play(mControllers.taskbarViewController.getTaskbarIconAlpha().get(
                TaskbarViewController.ALPHA_INDEX_NOTIFICATION_EXPANDED).animateToValue(alpha));
        anim.play(mControllers.taskbarDragLayerController.getNotificationShadeBgTaskbar()
                .animateToValue(alpha));
        anim.start();
        if (skipAnim) {
            anim.end();
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

    public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
        mControllers.navbarButtonsViewController.getTaskbarNavButtonDarkIntensity()
                .updateValue(darkIntensity);
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
            if (mIsFullscreen) {
                // We still need to be fullscreen, so defer any change to our height until we call
                // setTaskbarWindowFullscreen(false). For example, this could happen when dragging
                // from the gesture region, as the drag will cancel the gesture and reset launcher's
                // state, which in turn normally would reset the taskbar window height as well.
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

        boolean shouldTreatAsTransient = DisplayController.isTransientTaskbar(this)
                || (enableTaskbarPinning() && !isThreeButtonNav());

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

            return transientTaskbarDp.taskbarHeight
                    + (2 * transientTaskbarDp.taskbarBottomMargin)
                    + Math.max(extraHeightForTaskbarTooltips, resources.getDimensionPixelSize(
                    R.dimen.transient_taskbar_shadow_blur));
        }


        return mDeviceProfile.taskbarHeight
                + Math.max(getLeftCornerRadius(), getRightCornerRadius())
                + extraHeightForTaskbarTooltips;
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
     * Either adds or removes {@link WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE} on the taskbar
     * window.
     */
    public void setTaskbarWindowFocusable(boolean focusable) {
        if (focusable) {
            mWindowLayoutParams.flags &= ~FLAG_NOT_FOCUSABLE;
        } else {
            mWindowLayoutParams.flags |= FLAG_NOT_FOCUSABLE;
        }
        notifyUpdateLayoutParams();
    }

    /**
     * Applies forcibly show flag to taskbar window iff transient taskbar is unstashed.
     */
    public void applyForciblyShownFlagWhileTransientTaskbarUnstashed(boolean shouldForceShow) {
        if (!DisplayController.isTransientTaskbar(this)) {
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
     * Either adds or removes {@link WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE} on the taskbar
     * window. If we're now focusable, also move nav buttons to a separate window above IME.
     */
    public void setTaskbarWindowFocusableForIme(boolean focusable) {
        if (focusable) {
            mControllers.navbarButtonsViewController.moveNavButtonsToNewWindow();
        } else {
            mControllers.navbarButtonsViewController.moveNavButtonsBackToTaskbarWindow();
        }
        setTaskbarWindowFocusable(focusable);
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
        if (tag instanceof Task) {
            Task task = (Task) tag;
            ActivityManagerWrapper.getInstance().startActivityFromRecents(task.key,
                    ActivityOptions.makeBasic());
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
                        if (mIsSafeModeEnabled && !PackageManagerHelper.isSystemApp(this, intent)) {
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
            if (recents != null
                    && taskbarUIController.getRecentsView().isSplitSelectionActive()) {
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

        if (shouldCloseAllOpenViews) {
            AbstractFloatingView.closeAllOpenViews(this);
        }
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
        if (recents == null) {
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
                                && foundTaskView.isVisibleToUser()) {
                            TestLogging.recordEvent(
                                    TestProtocol.SEQUENCE_MAIN, "start: taskbarAppIcon");
                            foundTaskView.launchTasks();
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
                        startItemInfoActivity(itemInfos.get(0), foundTask);
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
            if (info.user.equals(Process.myUserHandle())) {
                // TODO(b/216683257): Use startActivityForResult for search results that require it.
                if (taskInRecents != null) {
                    // Re launch instance from recents
                    ActivityOptionsWrapper opts = getActivityLaunchOptions(null, info);
                    opts.options.setLaunchDisplayId(
                            getDisplay() == null ? DEFAULT_DISPLAY : getDisplay().getDisplayId());
                    if (ActivityManagerWrapper.getInstance()
                            .startActivityFromRecents(taskInRecents.key, opts.options)) {
                        mControllers.uiController.getRecentsView()
                                .addSideTaskLaunchCallback(opts.onEndCallback);
                        return;
                    }
                }

                startActivity(intent);
            } else {
                getSystemService(LauncherApps.class).startMainActivity(
                        intent.getComponent(), info.user, intent.getSourceBounds(), null);
            }
        } catch (NullPointerException | ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT)
                    .show();
            Log.e(TAG, "Unable to launch. tag=" + info + " intent=" + intent, e);
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

            folder.iterateOverItems((itemInfo, itemView) -> {
                mControllers.taskbarViewController
                        .setClickAndLongClickListenersForIcon(itemView);
                // To play haptic when dragging, like other Taskbar items do.
                itemView.setHapticFeedbackEnabled(true);
                return false;
            });
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
     */
    public void onSwipeToUnstashTaskbar() {
        boolean wasStashed = mControllers.taskbarStashController.isStashed();
        mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(/* stash= */ false);
        boolean isStashed = mControllers.taskbarStashController.isStashed();
        if (isStashed != wasStashed) {
            VibratorWrapper.INSTANCE.get(this).vibrateForTaskbarUnstash();
        }
        mControllers.taskbarEduTooltipController.hide();
    }

    /**
     * Called when we want to open bubblebar when user performs swipes up gesture.
     */
    public void onSwipeToOpenBubblebar() {
        mControllers.bubbleControllers.ifPresent(controllers -> {
            controllers.bubbleStashController.showBubbleBar(/* expandBubbles= */ true);
        });
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
        if (DisplayController.isTransientTaskbar(this)) {
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

    protected boolean isNavBarForceVisible() {
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
        if (allAppsButton != null && !FeatureFlags.ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT.get()) {
            ValueAnimator alphaOverride = ValueAnimator.ofFloat(0, 1);
            alphaOverride.setDuration(duration);
            alphaOverride.addUpdateListener(a -> {
                // Override the alpha updates in the icon alignment animation.
                allAppsButton.setAlpha(0);
            });
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
        if (mIsExcludeFromMagnificationRegion == exclude) {
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

    public boolean isInStashedLauncherState() {
        return mControllers.taskbarStashController.isInStashedLauncherState();
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
        pw.println(String.format(
                "%s\tmBindInProgress=%b", prefix, mBindingItems));
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

    boolean canToggleHomeAllApps() {
        return mControllers.uiController.canToggleHomeAllApps();
    }
}
