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

import static android.content.pm.PackageManager.FEATURE_PC;
import static android.os.Trace.TRACE_TAG_APP;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
import static android.window.SplashScreen.SPLASH_SCREEN_STYLE_UNDEFINED;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_OVERLAY_PROXY;
import static com.android.launcher3.Utilities.isRunningInTestHarness;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_OPEN;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_DRAGGING;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_FULLSCREEN;
import static com.android.launcher3.taskbar.TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW;
import static com.android.launcher3.testing.shared.ResourceUtils.getBoolByName;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo.Config;
import android.content.pm.LauncherApps;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Process;
import android.os.SystemProperties;
import android.os.Trace;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.RoundedCorner;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.AutohideSuspendFlag;
import com.android.launcher3.taskbar.TaskbarTranslationController.TransitionCallback;
import com.android.launcher3.taskbar.allapps.TaskbarAllAppsController;
import com.android.launcher3.taskbar.bubbles.BubbleBarController;
import com.android.launcher3.taskbar.bubbles.BubbleBarView;
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.taskbar.bubbles.BubbleStashController;
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemClickHandler.ItemClickProxy;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.ViewCache;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.rotation.RotationButtonController;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.unfold.updates.RotationChangeProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

import java.io.PrintWriter;
import java.util.Optional;

/**
 * The {@link ActivityContext} with which we inflate Taskbar-related Views. This allows UI elements
 * that are used by both Launcher and Taskbar (such as Folder) to reference a generic
 * ActivityContext and BaseDragLayer instead of the Launcher activity and its DragLayer.
 */
public class TaskbarActivityContext extends BaseTaskbarContext {

    private static final String IME_DRAWS_IME_NAV_BAR_RES_NAME = "config_imeDrawsImeNavBar";

    private static final boolean ENABLE_THREE_BUTTON_TASKBAR =
            SystemProperties.getBoolean("persist.debug.taskbar_three_button", false);
    private static final String TAG = "TaskbarActivityContext";

    private static final String WINDOW_TITLE = "Taskbar";

    private final TaskbarDragLayer mDragLayer;
    private final TaskbarControllers mControllers;

    private final WindowManager mWindowManager;
    private final @Nullable RoundedCorner mLeftCorner, mRightCorner;
    private DeviceProfile mDeviceProfile;
    private WindowManager.LayoutParams mWindowLayoutParams;
    private boolean mIsFullscreen;
    // The size we should return to when we call setTaskbarWindowFullscreen(false)
    private int mLastRequestedNonFullscreenHeight;

    private NavigationMode mNavMode;
    private final boolean mImeDrawsImeNavBar;
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

    public TaskbarActivityContext(Context windowContext, DeviceProfile launcherDp,
            TaskbarNavButtonController buttonController, ScopedUnfoldTransitionProgressProvider
            unfoldTransitionProgressProvider) {
        super(windowContext);
        final Resources resources = getResources();

        matchDeviceProfile(launcherDp, getResources());

        mNavMode = DisplayController.getNavigationMode(windowContext);
        mImeDrawsImeNavBar = getBoolByName(IME_DRAWS_IME_NAV_BAR_RES_NAME, resources, false);
        mIsSafeModeEnabled = TraceHelper.allowIpcs("isSafeMode",
                () -> getPackageManager().isSafeMode());
        SettingsCache settingsCache = SettingsCache.INSTANCE.get(this);
        mIsUserSetupComplete = settingsCache.getValue(
                Settings.Secure.getUriFor(Settings.Secure.USER_SETUP_COMPLETE), 0);
        mIsNavBarForceVisible = settingsCache.getValue(
                Settings.Secure.getUriFor(Settings.Secure.NAV_BAR_KIDS_MODE), 0);

        // TODO(b/244231596) For shared Taskbar window, update this value in init() instead so
        //  to get correct value when recreating the taskbar
        mIsNavBarKidsMode = settingsCache.getValue(
                Settings.Secure.getUriFor(Settings.Secure.NAV_BAR_KIDS_MODE), 0);

        // Get display and corners first, as views might use them in constructor.
        Display display = windowContext.getDisplay();
        Context c = display.getDisplayId() == Display.DEFAULT_DISPLAY
                ? windowContext.getApplicationContext()
                : windowContext.getApplicationContext().createDisplayContext(display);
        mWindowManager = c.getSystemService(WindowManager.class);
        mLeftCorner = display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
        mRightCorner = display.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);

        // Inflate views.
        boolean phoneMode = TaskbarManager.isPhoneMode(mDeviceProfile);
        int taskbarLayout = DisplayController.isTransientTaskbar(this) && !phoneMode
                ? R.layout.transient_taskbar
                : R.layout.taskbar;
        mDragLayer = (TaskbarDragLayer) mLayoutInflater.inflate(taskbarLayout, null, false);
        TaskbarView taskbarView = mDragLayer.findViewById(R.id.taskbar_view);
        TaskbarScrimView taskbarScrimView = mDragLayer.findViewById(R.id.taskbar_scrim);
        FrameLayout navButtonsView = mDragLayer.findViewById(R.id.navbuttons_view);
        StashedHandleView stashedHandleView = mDragLayer.findViewById(R.id.stashed_handle);
        BubbleBarView bubbleBarView = mDragLayer.findViewById(R.id.taskbar_bubbles);
        StashedHandleView bubbleHandleView = mDragLayer.findViewById(R.id.stashed_bubble_handle);

        mAccessibilityDelegate = new TaskbarShortcutMenuAccessibilityDelegate(this);

        final boolean isDesktopMode = getPackageManager().hasSystemFeature(FEATURE_PC);

        // If Bubble bar is present, TaskbarControllers depends on it so build it first.
        Optional<BubbleControllers> bubbleControllersOptional = Optional.empty();
        if (BubbleBarController.BUBBLE_BAR_ENABLED) {
            bubbleControllersOptional = Optional.of(new BubbleControllers(
                    new BubbleBarController(this, bubbleBarView),
                    new BubbleBarViewController(this, bubbleBarView),
                    new BubbleStashController(this),
                    new BubbleStashedHandleViewController(this, bubbleHandleView)));
        }

        // Construct controllers.
        mControllers = new TaskbarControllers(this,
                new TaskbarDragController(this),
                buttonController,
                isDesktopMode
                        ? new DesktopNavbarButtonsViewController(this, navButtonsView)
                        : new NavbarButtonsViewController(this, navButtonsView),
                new RotationButtonController(this,
                        c.getColor(R.color.floating_rotation_button_light_color),
                        c.getColor(R.color.floating_rotation_button_dark_color),
                        R.drawable.ic_sysbar_rotate_button_ccw_start_0,
                        R.drawable.ic_sysbar_rotate_button_ccw_start_90,
                        R.drawable.ic_sysbar_rotate_button_cw_start_0,
                        R.drawable.ic_sysbar_rotate_button_cw_start_90,
                        () -> getDisplay().getRotation()),
                new TaskbarDragLayerController(this, mDragLayer),
                new TaskbarViewController(this, taskbarView),
                new TaskbarScrimViewController(this, taskbarScrimView),
                new TaskbarUnfoldAnimationController(this, unfoldTransitionProgressProvider,
                    mWindowManager,
                    new RotationChangeProvider(c.getSystemService(DisplayManager.class), this,
                        getMainThreadHandler())),
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
                isDesktopMode
                        ? new DesktopTaskbarRecentAppsController(this)
                        : TaskbarRecentAppsController.DEFAULT,
                new TaskbarEduTooltipController(this),
                new KeyboardQuickSwitchController(),
                new TaskbarDividerPopupController(this),
                bubbleControllersOptional);
    }

    public void init(@NonNull TaskbarSharedState sharedState) {
        mLastRequestedNonFullscreenHeight = getDefaultTaskbarWindowHeight();
        mWindowLayoutParams =
                createDefaultWindowLayoutParams(TYPE_NAVIGATION_BAR_PANEL, WINDOW_TITLE);

        // Initialize controllers after all are constructed.
        mControllers.init(sharedState);
        updateSysuiStateFlags(sharedState.sysuiStateFlags, true /* fromInit */);
        disableNavBarElements(sharedState.disableNavBarDisplayId, sharedState.disableNavBarState1,
                sharedState.disableNavBarState2, false /* animate */);
        onSystemBarAttributesChanged(sharedState.systemBarAttrsDisplayId,
                sharedState.systemBarAttrsBehavior);
        onNavButtonsDarkIntensityChanged(sharedState.navButtonsDarkIntensity);

        if (FLAG_HIDE_NAVBAR_WINDOW) {
            // W/ the flag not set this entire class gets re-created, which resets the value of
            // mIsDestroyed. We re-use the class for small-screen, so we explicitly have to mark
            // this class as non-destroyed
            mIsDestroyed = false;
        }

        if (!mAddedWindow) {
            mWindowManager.addView(mDragLayer, mWindowLayoutParams);
            mAddedWindow = true;
        } else {
            mWindowManager.updateViewLayout(mDragLayer, mWindowLayoutParams);
        }
    }

    /**
     * Show Taskbar upon receiving broadcast
     */
    public void showTaskbarFromBroadcast() {
        mControllers.taskbarStashController.showTaskbarFromBroadcast();
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    /** Updates {@link DeviceProfile} instances for any Taskbar windows. */
    public void updateDeviceProfile(DeviceProfile launcherDp, NavigationMode navMode) {
        mNavMode = navMode;
        mControllers.taskbarOverlayController.updateLauncherDeviceProfile(launcherDp);
        matchDeviceProfile(launcherDp, getResources());

        AbstractFloatingView.closeAllOpenViewsExcept(this, false, TYPE_REBIND_SAFE);
        // Reapply fullscreen to take potential new screen size into account.
        setTaskbarWindowFullscreen(mIsFullscreen);

        dispatchDeviceProfileChanged();
    }

    @Override
    public void dispatchDeviceProfileChanged() {
        super.dispatchDeviceProfileChanged();
        Trace.instantForTrack(TRACE_TAG_APP, "TaskbarActivityContext#DeviceProfileChanged",
                getDeviceProfile().toSmallString());
    }

    /**
     * Copy the original DeviceProfile, match the number of hotseat icons and qsb width and update
     * the icon size
     */
    private void matchDeviceProfile(DeviceProfile originDeviceProfile, Resources resources) {
        mDeviceProfile = originDeviceProfile.toBuilder(this)
                .withDimensionsOverride(deviceProfile -> {
                    // Taskbar should match the number of icons of hotseat
                    deviceProfile.numShownHotseatIcons = originDeviceProfile.numShownHotseatIcons;
                    // Same QSB width to have a smooth animation
                    deviceProfile.hotseatQsbWidth = originDeviceProfile.hotseatQsbWidth;

                    // Update icon size
                    deviceProfile.iconSizePx = deviceProfile.taskbarIconSize;
                    deviceProfile.updateIconSize(1f, resources);
                }).build();
    }

    /**
     * Returns the View bounds of transient taskbar.
     */
    public Rect getTransientTaskbarBounds() {
        return mTransientTaskbarBounds;
    }

    @Override
    public StatsLogManager getStatsLogManager() {
        // Used to mock, can't mock a default interface method directly
        return super.getStatsLogManager();
    }

    /**
     * Creates LayoutParams for adding a view directly to WindowManager as a new window.
     * @param type The window type to pass to the created WindowManager.LayoutParams.
     * @param title The window title to pass to the created WindowManager.LayoutParams.
     */
    public WindowManager.LayoutParams createDefaultWindowLayoutParams(int type, String title) {
        DeviceProfile deviceProfile = getDeviceProfile();
        // Taskbar is on the logical bottom of the screen
        boolean isVerticalBarLayout = TaskbarManager.isPhoneButtonNavMode(this) &&
                deviceProfile.isLandscape;

        int windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_SLIPPERY
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
        if (DisplayController.isTransientTaskbar(this) && !isRunningInTestHarness()) {
            windowFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        }
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams(
                isVerticalBarLayout ? mLastRequestedNonFullscreenHeight : MATCH_PARENT,
                isVerticalBarLayout ? MATCH_PARENT : mLastRequestedNonFullscreenHeight,
                type,
                windowFlags,
                PixelFormat.TRANSLUCENT);
        windowLayoutParams.setTitle(title);
        windowLayoutParams.packageName = getPackageName();
        windowLayoutParams.gravity = !isVerticalBarLayout ?
                Gravity.BOTTOM :
                Gravity.END; // TODO(b/230394142): seascape

        windowLayoutParams.setFitInsetsTypes(0);
        windowLayoutParams.receiveInsetsIgnoringZOrder = true;
        windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        windowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        windowLayoutParams.privateFlags =
                WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
        windowLayoutParams.accessibilityTitle = getString(
                TaskbarManager.isPhoneMode(mDeviceProfile)
                        ? R.string.taskbar_phone_a11y_title
                        : R.string.taskbar_a11y_title);
        return windowLayoutParams;
    }

    public void onConfigurationChanged(@Config int configChanges) {
        mControllers.onConfigurationChanged(configChanges);
        if (!mIsUserSetupComplete) {
            setTaskbarWindowHeight(getSetupWindowHeight());
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
        if (mControllers.uiController.isInOverview()) {
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
        ActivityOptions options = ActivityOptions.makeCustomAnimation(
                this, 0, 0, Color.TRANSPARENT,
                Executors.MAIN_EXECUTOR.getHandler(), null,
                elapsedRealTime -> callbacks.executeAllAndDestroy());
        options.setSplashScreenStyle(splashScreenStyle);
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
        if (!FLAG_HIDE_NAVBAR_WINDOW) {
            mWindowManager.removeViewImmediate(mDragLayer);
            mAddedWindow = false;
        }
    }

    public boolean isDestroyed() {
        return mIsDestroyed;
    }

    public void updateSysuiStateFlags(int systemUiStateFlags, boolean fromInit) {
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
        setTaskbarWindowHeight(fullscreen ? MATCH_PARENT : mLastRequestedNonFullscreenHeight);
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
     * Updates the TaskbarContainer height (pass {@link #getDefaultTaskbarWindowHeight()} to reset).
     */
    public void setTaskbarWindowHeight(int height) {
        if (mWindowLayoutParams.height == height || mIsDestroyed) {
            return;
        }
        if (height == MATCH_PARENT) {
            height = mDeviceProfile.heightPx;
        } else {
            mLastRequestedNonFullscreenHeight = height;
            if (mIsFullscreen) {
                // We still need to be fullscreen, so defer any change to our height until we call
                // setTaskbarWindowFullscreen(false). For example, this could happen when dragging
                // from the gesture region, as the drag will cancel the gesture and reset launcher's
                // state, which in turn normally would reset the taskbar window height as well.
                return;
            }
        }
        mWindowLayoutParams.height = height;
        mControllers.taskbarInsetsController.onTaskbarWindowHeightOrInsetsChanged();
        mWindowManager.updateViewLayout(mDragLayer, mWindowLayoutParams);
    }

    /**
     * Returns the default height of the window, including the static corner radii above taskbar.
     */
    public int getDefaultTaskbarWindowHeight() {
        Resources resources = getResources();

        if (FLAG_HIDE_NAVBAR_WINDOW && mDeviceProfile.isPhone) {
            return isThreeButtonNav() ?
                    resources.getDimensionPixelSize(R.dimen.taskbar_size) :
                    resources.getDimensionPixelSize(R.dimen.taskbar_stashed_size);
        }

        if (!isUserSetupComplete()) {
            return getSetupWindowHeight();
        }

        if (DisplayController.isTransientTaskbar(this)) {
            return mDeviceProfile.taskbarHeight
                    + (2 * mDeviceProfile.taskbarBottomMargin)
                    + resources.getDimensionPixelSize(R.dimen.transient_taskbar_shadow_blur);
        }

        return mDeviceProfile.taskbarHeight
                + Math.max(getLeftCornerRadius(), getRightCornerRadius());
    }

    public int getSetupWindowHeight() {
        return getResources().getDimensionPixelSize(R.dimen.taskbar_suw_frame);
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
        mWindowManager.updateViewLayout(mDragLayer, mWindowLayoutParams);
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
        boolean shouldCloseAllOpenViews = true;
        Object tag = view.getTag();
        if (tag instanceof Task) {
            Task task = (Task) tag;
            ActivityManagerWrapper.getInstance().startActivityFromRecents(task.key,
                    ActivityOptions.makeBasic());
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
        } else if (tag instanceof FolderInfo) {
            shouldCloseAllOpenViews = false;
            FolderIcon folderIcon = (FolderIcon) view;
            Folder folder = folderIcon.getFolder();

            folder.setOnFolderStateChangedListener(newState -> {
                if (newState == Folder.STATE_OPEN) {
                    setTaskbarWindowFocusableForIme(true);
                } else if (newState == Folder.STATE_CLOSED) {
                    // Defer by a frame to ensure we're no longer fullscreen and thus won't jump.
                    getDragLayer().post(() -> setTaskbarWindowFocusableForIme(false));
                    folder.setOnFolderStateChangedListener(null);
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
        } else if (tag instanceof WorkspaceItemInfo) {
            // Tapping a launchable icon on Taskbar
            WorkspaceItemInfo info = (WorkspaceItemInfo) tag;
            if (!info.isDisabled() || !ItemClickHandler.handleDisabledItemClicked(info, this)) {
                TaskbarUIController taskbarUIController = mControllers.uiController;
                RecentsView recents = taskbarUIController.getRecentsView();
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
                            intent = new PackageManagerHelper(this)
                                    .getMarketIntent(info.getTargetPackage())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);

                        } else if (info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                            TestLogging.recordEvent(
                                    TestProtocol.SEQUENCE_MAIN, "start: taskbarDeepShortcut");
                            String id = info.getDeepShortcutId();
                            String packageName = intent.getPackage();
                            getSystemService(LauncherApps.class)
                                    .startShortcut(packageName, id, null, null, info.user);
                        } else {
                            launchFromTaskbarPreservingSplitIfVisible(recents, info);
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
                mControllers.uiController.onTaskbarIconLaunched(info);
                mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
            }
        } else if (tag instanceof AppInfo) {
            // Tapping an item in AllApps
            AppInfo info = (AppInfo) tag;
            TaskbarUIController taskbarUIController = mControllers.uiController;
            RecentsView recents = taskbarUIController.getRecentsView();
            if (recents != null
                    && taskbarUIController.getRecentsView().isSplitSelectionActive()) {
                // If we are selecting a second app for split, launch the split tasks
                taskbarUIController.triggerSecondAppForSplit(info, info.intent, view);
            } else {
                launchFromTaskbarPreservingSplitIfVisible(recents, info);
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
     * Run when the user taps a Taskbar icon while in Overview. If the tapped app is currently
     * visible to the user in Overview, or is part of a visible split pair, we expand the TaskView
     * as if the user tapped on it (preserving the split pair). Otherwise, launch it normally
     * (potentially breaking a split pair).
     */
    private void launchFromTaskbarPreservingSplitIfVisible(@Nullable RecentsView recents,
            ItemInfo info) {
        if (recents == null) {
            return;
        }
        recents.getSplitSelectController().findLastActiveTaskAndRunCallback(
                info.getComponentKey(),
                foundTask -> {
                    if (foundTask != null) {
                        TaskView foundTaskView =
                                recents.getTaskViewByTaskId(foundTask.key.id);
                        if (foundTaskView != null
                                && foundTaskView.isVisibleToUser()) {
                            TestLogging.recordEvent(
                                    TestProtocol.SEQUENCE_MAIN, "start: taskbarAppIcon");
                            foundTaskView.launchTasks();
                            return;
                        }
                    }
                    startItemInfoActivity(info);
                });
    }

    private void startItemInfoActivity(ItemInfo info) {
        Intent intent = new Intent(info.getIntent())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: taskbarAppIcon");
            if (info.user.equals(Process.myUserHandle())) {
                // TODO(b/216683257): Use startActivityForResult for search results that require it.
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

    /**
     * Returns whether the taskbar is currently visually stashed.
     */
    public boolean isTaskbarStashed() {
        return mControllers.taskbarStashController.isStashed();
    }

    /**
     * Called when we detect a long press in the nav region before passing the gesture slop.
     * @return Whether taskbar handled the long press, and thus should cancel the gesture.
     */
    public boolean onLongPressToUnstashTaskbar() {
        return mControllers.taskbarStashController.onLongPressToUnstashTaskbar();
    }

    /**
     * Called when we want to unstash taskbar when user performs swipes up gesture.
     */
    public void onSwipeToUnstashTaskbar() {
        mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(false);
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
        // TODO(b/270395798): Clean up forceUnstash after removing long-press unstashing code.
        startTaskbarUnstashHint(animateForward, /* forceUnstash = */ false);
    }

    /**
     * Called when we detect a motion down or up/cancel in the nav region while stashed.
     *
     * @param animateForward Whether to animate towards the unstashed hint state or back to stashed.
     * @param forceUnstash Whether we force the unstash hint.
     */
    public void startTaskbarUnstashHint(boolean animateForward, boolean forceUnstash) {
        // TODO(b/270395798): Clean up forceUnstash after removing long-press unstashing code.
        mControllers.taskbarStashController.startUnstashHint(animateForward, forceUnstash);
    }

    /**
     * Enables manual taskbar stashing. This method should only be used for tests that need to
     * stash/unstash the taskbar.
     */
    @VisibleForTesting
    public void enableManualStashingDuringTests(boolean enableManualStashing) {
        mControllers.taskbarStashController.enableManualStashingDuringTests(enableManualStashing);
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
     * Unstashes the Taskbar if it is stashed. This method should only be used to unstash the
     * taskbar at the end of a test.
     */
    @VisibleForTesting
    public void unstashTaskbarIfStashed() {
        if (DisplayController.isTransientTaskbar(this)) {
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(false);
        } else {
            mControllers.taskbarStashController.onLongPressToUnstashTaskbar();
        }
    }

    protected boolean isUserSetupComplete() {
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
            ((LauncherTaskbarUIController) uiController).addLauncherResumeAnimation(
                    fullAnimation, duration);
        }
        mControllers.taskbarStashController.addUnstashToHotseatAnimation(fullAnimation, duration);

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
        mWindowManager.updateViewLayout(mDragLayer, mWindowLayoutParams);
    }

    void notifyUpdateLayoutParams() {
        if (mDragLayer.isAttachedToWindow()) {
            mWindowManager.updateViewLayout(mDragLayer, mWindowLayoutParams);
        }
    }

    public void showPopupMenuForIcon(BubbleTextView btv) {
        setTaskbarWindowFullscreen(true);
        btv.post(() -> mControllers.taskbarPopupController.showForIcon(btv));
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
}
