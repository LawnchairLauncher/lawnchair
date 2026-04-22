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

import static android.view.View.AccessibilityDelegate;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.launcher3.LauncherAnimUtils.ROTATION_DRAWABLE_PERCENT;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.SYSUI_SURFACE_PROGRESS_INDEX;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_A11Y;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_BACK;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_HOME;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_IME_SWITCH;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_RECENTS;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_SPACE;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_KEYGUARD;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_SMALL_SCREEN;
import static com.android.launcher3.util.FlagDebugUtils.appendFlag;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BACK_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BACK_DISMISS_IME;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DISABLE_GESTURE_SPLIT_INVOCATION;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_SWITCHER_BUTTON_VISIBLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_VISIBLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NAV_BAR_HIDDEN;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SHORTCUT_HELPER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING;
import static com.android.window.flags2.Flags.predictiveBackThreeButtonNav;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.DrawableRes;
import android.annotation.IdRes;
import android.annotation.LayoutRes;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo.Config;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.RotateDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.util.Property;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.Flags;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarButton;
import com.android.launcher3.taskbar.bubbles.BubbleBarController;
import com.android.launcher3.taskbar.navbutton.NavButtonLayoutFactory;
import com.android.launcher3.taskbar.navbutton.NavButtonLayoutFactory.NavButtonLayoutter;
import com.android.launcher3.taskbar.navbutton.NearestTouchFrame;
import com.android.launcher3.util.DimensionUtils;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.views.BaseDragLayer;
import com.android.systemui.shared.navigationbar.KeyButtonRipple;
import com.android.systemui.shared.rotation.FloatingRotationButton;
import com.android.systemui.shared.rotation.RotationButton;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;

/**
 * Controller for managing nav bar buttons in taskbar
 */
public class NavbarButtonsViewController implements TaskbarControllers.LoggableTaskbarController,
        BubbleBarController.BubbleBarLocationListener {

    private final Rect mTempRect = new Rect();

    /** Whether the IME Switcher button is visible. */
    private static final int FLAG_IME_SWITCHER_BUTTON_VISIBLE = 1 << 0;
    /** Whether the IME is visible. */
    private static final int FLAG_IME_VISIBLE = 1 << 1;
    /**
     * The back button is visually adjusted to indicate that it will dismiss the IME when pressed.
     * This only takes effect while the IME is visible. By default, it is set while the IME is
     * visible, but may be overridden by the
     * {@link android.inputmethodservice.InputMethodService.BackDispositionMode backDispositionMode}
     * set by the IME.
     */
    private static final int FLAG_BACK_DISMISS_IME = 1 << 2;
    private static final int FLAG_A11Y_VISIBLE = 1 << 3;
    private static final int FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE = 1 << 4;
    private static final int FLAG_KEYGUARD_VISIBLE = 1 << 5;
    private static final int FLAG_KEYGUARD_OCCLUDED = 1 << 6;
    private static final int FLAG_DISABLE_HOME = 1 << 7;
    private static final int FLAG_DISABLE_RECENTS = 1 << 8;
    private static final int FLAG_DISABLE_BACK = 1 << 9;
    private static final int FLAG_NOTIFICATION_SHADE_EXPANDED = 1 << 10;
    private static final int FLAG_SCREEN_PINNING_ACTIVE = 1 << 11;
    private static final int FLAG_VOICE_INTERACTION_WINDOW_SHOWING = 1 << 12;
    private static final int FLAG_SMALL_SCREEN = 1 << 13;
    private static final int FLAG_SLIDE_IN_VIEW_VISIBLE = 1 << 14;
    private static final int FLAG_KEYBOARD_SHORTCUT_HELPER_SHOWING = 1 << 15;

    /**
     * Flags where a UI could be over Taskbar surfaces, so the color override should be disabled.
     */
    private static final int FLAGS_ON_BACKGROUND_COLOR_OVERRIDE_DISABLED =
            FLAG_NOTIFICATION_SHADE_EXPANDED | FLAG_VOICE_INTERACTION_WINDOW_SHOWING;

    private static final String NAV_BUTTONS_SEPARATE_WINDOW_TITLE = "Taskbar Nav Buttons";
    private static final String SUW_THEME_SYSTEM_PROPERTY = "setupwizard.theme";
    private static final String GLIF_EXPRESSIVE_THEME = "glif_expressive";
    private static final String GLIF_EXPRESSIVE_LIGHT_THEME = "glif_expressive_light";

    private static final double SQUARE_ASPECT_RATIO_BOTTOM_BOUND = 0.95;
    private static final double SQUARE_ASPECT_RATIO_UPPER_BOUND = 1.05;

    public static final int ALPHA_INDEX_IMMERSIVE_MODE = 0;
    public static final int ALPHA_INDEX_KEYGUARD_OR_DISABLE = 1;
    public static final int ALPHA_INDEX_SUW = 2;
    private static final int NUM_ALPHA_CHANNELS = 3;

    private static final long AUTODIM_TIMEOUT_MS = 2250;
    private static final long PREDICTIVE_BACK_TIMEOUT_MS = 200;

    private final ArrayList<StatePropertyHolder> mPropertyHolders = new ArrayList<>();
    private final ArrayList<ImageView> mAllButtons = new ArrayList<>();
    private int mState;

    private final TaskbarActivityContext mContext;
    private final @Nullable Context mNavigationBarPanelContext;
    private final WindowManagerProxy mWindowManagerProxy;
    private final NearestTouchFrame mNavButtonsView;
    private final Handler mHandler;
    private final LinearLayout mNavButtonContainer;
    // Used for IME+A11Y buttons
    private final ViewGroup mEndContextualContainer;
    private final ViewGroup mStartContextualContainer;
    private final int mLightIconColorOnWorkspace;
    private final int mDarkIconColorOnWorkspace;
    /** Color to use for navbar buttons, if they are on on a Taskbar surface background. */
    private final int mOnBackgroundIconColor;
    private final boolean mIsExpressiveThemeEnabled;

    private @Nullable Animator mNavBarLocationAnimator;
    private @Nullable BubbleBarLocation mBubbleBarTargetLocation;

    private final AnimatedFloat mTaskbarNavButtonTranslationY = new AnimatedFloat(
            this::updateNavButtonTranslationY);
    private final AnimatedFloat mTaskbarNavButtonTranslationYForInAppDisplay = new AnimatedFloat(
            this::updateNavButtonTranslationY);
    private final AnimatedFloat mTaskbarNavButtonTranslationYForIme = new AnimatedFloat(
            this::updateNavButtonTranslationY);
    private float mLastSetNavButtonTranslationY;
    // Used for System UI state updates that should translate the nav button for in-app display.
    private final AnimatedFloat mNavButtonInAppDisplayProgressForSysui = new AnimatedFloat(
            this::updateNavButtonInAppDisplayProgressForSysui);
    /**
     * Expected nav button dark intensity piped down from {@code LightBarController} in framework
     * via {@code TaskbarDelegate}.
     */
    private final AnimatedFloat mTaskbarNavButtonDarkIntensity = new AnimatedFloat(
            this::onDarkIntensityChanged);
    /** {@code 1} if the Taskbar background color is fully opaque. */
    private final AnimatedFloat mOnTaskbarBackgroundNavButtonColorOverride = new AnimatedFloat(
            this::updateNavButtonColor);
    /** {@code 1} if a Taskbar slide in overlay is visible over Taskbar. */
    private final AnimatedFloat mSlideInViewVisibleNavButtonColorOverride = new AnimatedFloat(
            this::updateNavButtonColor);
    /** Disables the {@link #mOnBackgroundIconColor} override if {@code 0}. */
    private final AnimatedFloat mOnBackgroundNavButtonColorOverrideMultiplier = new AnimatedFloat(
            this::updateNavButtonColor);
    private final RotationButtonListener mRotationButtonListener = new RotationButtonListener();

    private final Rect mFloatingRotationButtonBounds = new Rect();

    // Initialized in init.
    private TaskbarControllers mControllers;
    private boolean mIsImeRenderingNavButtons;
    private ImageView mA11yButton;
    @SystemUiStateFlags
    private long mSysuiStateFlags;
    private ImageView mBackButton;
    private ImageView mHomeButton;
    private MultiValueAlpha mBackButtonAlpha;
    private MultiValueAlpha mHomeButtonAlpha;
    private FloatingRotationButton mFloatingRotationButton;
    private ImageView mImeSwitcherButton;

    // Variables for moving nav buttons to a separate window above IME
    private boolean mAreNavButtonsInSeparateWindow = false;
    private BaseDragLayer<TaskbarActivityContext> mSeparateWindowParent; // Initialized in init.
    private final ViewTreeObserver.OnComputeInternalInsetsListener mSeparateWindowInsetsComputer =
            this::onComputeInsetsForSeparateWindow;
    private final RecentsHitboxExtender mHitboxExtender = new RecentsHitboxExtender();
    private ImageView mRecentsButton;
    private Space mSpace;

    private TaskbarTransitions mTaskbarTransitions;
    private @BarTransitions.TransitionMode int mTransitionMode;

    private final Runnable mAutoDim = () -> mTaskbarTransitions.setAutoDim(true);

    public NavbarButtonsViewController(TaskbarActivityContext context,
            @Nullable Context navigationBarPanelContext, NearestTouchFrame navButtonsView,
            Handler handler) {
        mContext = context;
        mNavigationBarPanelContext = navigationBarPanelContext;
        mWindowManagerProxy = WindowManagerProxy.INSTANCE.get(mContext);
        mNavButtonsView = navButtonsView;
        mHandler = handler;
        mNavButtonContainer = mNavButtonsView.findViewById(R.id.end_nav_buttons);
        mEndContextualContainer = mNavButtonsView.findViewById(R.id.end_contextual_buttons);
        mStartContextualContainer = mNavButtonsView.findViewById(R.id.start_contextual_buttons);

        mLightIconColorOnWorkspace = context.getColor(R.color.taskbar_nav_icon_light_color_on_home);
        mDarkIconColorOnWorkspace = context.getColor(R.color.taskbar_nav_icon_dark_color_on_home);
        mOnBackgroundIconColor = Utilities.isDarkTheme(context)
                ? context.getColor(R.color.taskbar_nav_icon_light_color)
                : context.getColor(R.color.taskbar_nav_icon_dark_color);

        if (mContext.isPhoneMode()) {
            mTaskbarTransitions = new TaskbarTransitions(mContext, mNavButtonsView);
        }
        String SUWTheme = SystemProperties.get(SUW_THEME_SYSTEM_PROPERTY, "");
        mIsExpressiveThemeEnabled = SUWTheme.equals(GLIF_EXPRESSIVE_THEME)
                || SUWTheme.equals(GLIF_EXPRESSIVE_LIGHT_THEME);
    }

    /**
     * Initializes the controller
     */
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        setupController();
    }

    protected void setupController() {
        final boolean isThreeButtonNav = mContext.isThreeButtonNav();
        final boolean isPhoneMode = mContext.isPhoneMode();
        DeviceProfile deviceProfile = mContext.getDeviceProfile();
        Resources resources = mContext.getResources();

        int setupSize = mControllers.taskbarActivityContext.getSetupWindowSize();
        Point p = DimensionUtils.getTaskbarPhoneDimensions(deviceProfile, resources, isPhoneMode,
                mContext.isGestureNav());
        ViewGroup.LayoutParams navButtonsViewLayoutParams = mNavButtonsView.getLayoutParams();
        navButtonsViewLayoutParams.width = p.x;
        if (!mContext.isUserSetupComplete()) {
            // Setup mode in phone mode uses gesture nav.
            navButtonsViewLayoutParams.height = setupSize;
        } else {
            navButtonsViewLayoutParams.height = p.y;
        }
        mNavButtonsView.setLayoutParams(navButtonsViewLayoutParams);

        mIsImeRenderingNavButtons = mContext.imeDrawsImeNavBar();
        if (!mIsImeRenderingNavButtons) {
            // IME switcher
            final int switcherResId = Flags.imeSwitcherRevamp()
                    ? com.android.internal.R.drawable.ic_ime_switcher_new
                    : R.drawable.ic_ime_switcher;
            mImeSwitcherButton = addButton(switcherResId, BUTTON_IME_SWITCH,
                    isThreeButtonNav ? mStartContextualContainer : mEndContextualContainer,
                    mControllers.navButtonController, R.id.ime_switcher);
            // A11y and IME Switcher buttons overlap on phone mode, show only a11y if both visible.
            mPropertyHolders.add(new StatePropertyHolder(mImeSwitcherButton,
                    flags -> (flags & FLAG_IME_SWITCHER_BUTTON_VISIBLE) != 0
                            && !(isPhoneMode && (flags & FLAG_A11Y_VISIBLE) != 0)));
        }

        mPropertyHolders.add(new StatePropertyHolder(
                mControllers.taskbarViewController.getTaskbarIconAlpha()
                        .get(ALPHA_INDEX_KEYGUARD),
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0
                        && (flags & FLAG_SCREEN_PINNING_ACTIVE) == 0));

        mPropertyHolders.add(new StatePropertyHolder(
                mControllers.taskbarViewController.getTaskbarIconAlpha()
                        .get(ALPHA_INDEX_SMALL_SCREEN),
                flags -> (flags & FLAG_SMALL_SCREEN) == 0));

        if (!isPhoneMode) {
            mPropertyHolders.add(new StatePropertyHolder(mControllers.taskbarDragLayerController
                    .getKeyguardBgTaskbar(), flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0));
        }

        // Start at 1 because relevant flags are unset at init.
        mOnBackgroundNavButtonColorOverrideMultiplier.value = 1;

        // Potentially force the back button to be visible during setup wizard. The back button
        // won't show up if the expressive theme is enabled and simple view is disabled
        boolean shouldShowInSetup = !mContext.isUserSetupComplete()
                && (!mIsExpressiveThemeEnabled || mContext.isSimpleViewEnabled());
        boolean isInKidsMode = mContext.isNavBarKidsModeActive();
        boolean alwaysShowButtons = isThreeButtonNav || shouldShowInSetup;

        // Make sure to remove nav bar buttons translation when any of the following occur:
        // - Notification shade is expanded
        // - IME is visible (add separate translation for IME)
        // - VoiceInteractionWindow (assistant) is showing
        // - Keyboard shortcuts helper is showing
        if (!isPhoneMode) {
            int flagsToRemoveTranslation = FLAG_NOTIFICATION_SHADE_EXPANDED | FLAG_IME_VISIBLE
                    | FLAG_VOICE_INTERACTION_WINDOW_SHOWING | FLAG_KEYBOARD_SHORTCUT_HELPER_SHOWING;
            mPropertyHolders.add(new StatePropertyHolder(mNavButtonInAppDisplayProgressForSysui,
                    flags -> (flags & flagsToRemoveTranslation) != 0, AnimatedFloat.VALUE,
                    1, 0));
            // Center nav buttons in new height for IME.
            float transForIme = (mContext.getDeviceProfile().getTaskbarProfile().getHeight()
                    - mControllers.taskbarInsetsController.getTaskbarHeightForIme()) / 2f;
            // For gesture nav, nav buttons only show for IME anyway so keep them translated down.
            float defaultButtonTransY = alwaysShowButtons ? 0 : transForIme;
            mPropertyHolders.add(new StatePropertyHolder(mTaskbarNavButtonTranslationYForIme,
                    flags -> (flags & FLAG_IME_VISIBLE) != 0 && !isInKidsMode, AnimatedFloat.VALUE,
                    transForIme, defaultButtonTransY));

            mPropertyHolders.add(new StatePropertyHolder(
                    mOnBackgroundNavButtonColorOverrideMultiplier,
                    flags -> (flags & FLAGS_ON_BACKGROUND_COLOR_OVERRIDE_DISABLED) == 0));

            mPropertyHolders.add(new StatePropertyHolder(
                    mSlideInViewVisibleNavButtonColorOverride,
                    flags -> (flags & FLAG_SLIDE_IN_VIEW_VISIBLE) != 0));
        }

        if (alwaysShowButtons) {
            initButtons(mNavButtonContainer, mEndContextualContainer,
                    mControllers.navButtonController);
            updateButtonLayoutSpacing();
            updateStateForFlag(FLAG_SMALL_SCREEN, isPhoneMode);

            if (!isPhoneMode) {
                mPropertyHolders.add(new StatePropertyHolder(
                        mControllers.taskbarDragLayerController.getNavbarBackgroundAlpha(),
                        flags -> (flags & FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE) != 0));
            }
        }
        mFloatingRotationButton = new FloatingRotationButton(
                ENABLE_TASKBAR_NAVBAR_UNIFICATION ? mNavigationBarPanelContext : mContext,
                R.string.accessibility_rotate_button,
                R.layout.rotate_suggestion,
                R.id.rotate_suggestion,
                R.dimen.floating_rotation_button_min_margin,
                R.dimen.rounded_corner_content_padding,
                R.dimen.floating_rotation_button_taskbar_left_margin,
                R.dimen.floating_rotation_button_taskbar_bottom_margin,
                R.dimen.floating_rotation_button_diameter,
                R.dimen.key_button_ripple_max_width,
                R.bool.floating_rotation_button_position_left);
        mControllers.rotationButtonController.setRotationButton(mFloatingRotationButton,
                mRotationButtonListener);
        if (isPhoneMode) {
            mTaskbarTransitions.init();
        }

        applyState();
        mPropertyHolders.forEach(StatePropertyHolder::endAnimation);

        // Initialize things needed to move nav buttons to separate window.
        mSeparateWindowParent = new BaseDragLayer<>(mContext, null, 0) {
            @Override
            public void recreateControllers() {
                super.recreateControllers();
                mControllers = new TouchController[0];
            }

            @Override
            protected boolean canFindActiveController() {
                // We don't have any controllers, but we don't want any floating views such as
                // folder to intercept, either. This ensures nav buttons can always be pressed.
                return false;
            }
        };
        mSeparateWindowParent.recreateControllers();
        if (BubbleBarController.isBubbleBarEnabled()) {
            mNavButtonsView.addOnLayoutChangeListener(
                    (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                            onLayoutsUpdated()
            );
        }
    }

    private void initButtons(ViewGroup navContainer, ViewGroup endContainer,
            TaskbarNavButtonController navButtonController) {

        mBackButton = addButton(R.drawable.ic_sysbar_back, BUTTON_BACK,
                mNavButtonContainer, mControllers.navButtonController, R.id.back);
        mBackButtonAlpha = new MultiValueAlpha(mBackButton, NUM_ALPHA_CHANNELS);
        mBackButtonAlpha.setUpdateVisibility(true);
        mPropertyHolders.add(new StatePropertyHolder(
                mBackButtonAlpha.get(ALPHA_INDEX_KEYGUARD_OR_DISABLE),
                flags -> {
                    // Show only if not disabled, and if not on the keyguard or otherwise only when
                    // the bouncer or a lockscreen app is showing above the keyguard
                    boolean showingOnKeyguard = (flags & FLAG_KEYGUARD_VISIBLE) == 0 ||
                            (flags & FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE) != 0 ||
                            (flags & FLAG_KEYGUARD_OCCLUDED) != 0;
                    return (flags & FLAG_DISABLE_BACK) == 0
                            && (!mContext.isGestureNav() || !mContext.isUserSetupComplete())
                            && ((flags & FLAG_KEYGUARD_VISIBLE) == 0 || showingOnKeyguard);
                }));
        // Hide back button in SUW if keyboard is showing (IME draws its own back).
        if (mIsImeRenderingNavButtons) {
            mPropertyHolders.add(new StatePropertyHolder(
                    mBackButtonAlpha.get(ALPHA_INDEX_SUW),
                    flags -> (flags & FLAG_IME_VISIBLE) == 0));
        }
        mPropertyHolders.add(new StatePropertyHolder(mBackButton,
                flags -> (flags & FLAG_BACK_DISMISS_IME) != 0,
                ROTATION_DRAWABLE_PERCENT, 1f, 0f));
        // Translate back button to be at end/start of other buttons for keyguard (only after SUW
        // since it is laid to align with SUW actions while in that state)
        int navButtonSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.taskbar_nav_buttons_size);
        boolean isRtl = Utilities.isRtl(mContext.getResources());
        if (!mContext.isPhoneMode()) {
            mPropertyHolders.add(new StatePropertyHolder(
                    mBackButton, flags -> mContext.isUserSetupComplete()
                        && ((flags & FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE) != 0
                            || (flags & FLAG_KEYGUARD_VISIBLE) != 0)
                        && (!shouldShowHomeButtonInLockscreen(flags)),
                    VIEW_TRANSLATE_X, navButtonSize * (isRtl ? -2 : 2), 0));
        }

        // home button
        mHomeButton = addButton(R.drawable.ic_sysbar_home, BUTTON_HOME, navContainer,
                navButtonController, R.id.home);
        mHomeButtonAlpha = new MultiValueAlpha(mHomeButton, NUM_ALPHA_CHANNELS);
        mHomeButtonAlpha.setUpdateVisibility(true);
        mPropertyHolders.add(
                new StatePropertyHolder(mHomeButtonAlpha.get(ALPHA_INDEX_KEYGUARD_OR_DISABLE),
                        this::shouldShowHomeButtonInLockscreen));

        // Recents button
        mRecentsButton = addButton(R.drawable.ic_sysbar_recent, BUTTON_RECENTS,
                navContainer, navButtonController, R.id.recent_apps);
        mHitboxExtender.init(mRecentsButton, mNavButtonsView, mContext.getDeviceProfile(),
                () -> {
                    float[] recentsCoords = new float[2];
                    getDescendantCoordRelativeToAncestor(mRecentsButton, mNavButtonsView,
                            recentsCoords, false);
                    return recentsCoords;
                }, new Handler());
        mRecentsButton.setOnClickListener(v -> {
            navButtonController.onButtonClick(BUTTON_RECENTS, v);
            mHitboxExtender.onRecentsButtonClicked();
        });
        mRecentsButton.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    int[] location = v.getLocationOnScreen();
                    Rect bounds = new Rect(location[0], location[1], location[0] + v.getWidth(),
                            location[1] + v.getHeight());
                    navButtonController.onRecentsButtonLayoutChanged(bounds);
                });
        mPropertyHolders.add(new StatePropertyHolder(mRecentsButton,
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0 && (flags & FLAG_DISABLE_RECENTS) == 0
                        && !mContext.isNavBarKidsModeActive() && !mContext.isGestureNav()));

        // A11y button
        mA11yButton = addButton(R.drawable.ic_sysbar_accessibility_button, BUTTON_A11Y,
                endContainer, navButtonController, R.id.accessibility_button,
                R.layout.taskbar_contextual_button);
        mPropertyHolders.add(new StatePropertyHolder(mA11yButton,
                flags -> (flags & FLAG_A11Y_VISIBLE) != 0));

        mSpace = new Space(mNavButtonsView.getContext());
        mSpace.setOnClickListener(view -> navButtonController.onButtonClick(BUTTON_SPACE, view));
        mSpace.setOnLongClickListener(view ->
                navButtonController.onButtonLongClick(BUTTON_SPACE, view));
    }

    /**
     * Method to determine whether the Navigation Bar is viewable in Setup Wizard
     *
     * @return {@code true} if the device is in Setup Wizard, the expressive theme is enabled,
     * and Simple View is NOT enabled
     */
    boolean isNavbarHiddenInSUW() {
        if (mContext == null) {
            return false;
        }
        return !mContext.isUserSetupComplete() && mIsExpressiveThemeEnabled
                && !mContext.isSimpleViewEnabled();
    }

    /**
     * Method to determine whether to show the home button in lockscreen
     *
     * When the keyguard is visible hide home button. Anytime we are
     * occluded we want to show the home button for apps over keyguard.
     * however we don't want to show when not occluded/visible.
     * (visible false || occluded true) && disable false && not gnav
     */
    private boolean shouldShowHomeButtonInLockscreen(int flags) {
        return ((flags & FLAG_KEYGUARD_VISIBLE) == 0
                || (flags & FLAG_KEYGUARD_OCCLUDED) != 0)
                && (flags & FLAG_DISABLE_HOME) == 0
                && !mContext.isGestureNav();
    }

    private void parseSystemUiFlags(@SystemUiStateFlags long sysUiStateFlags) {
        mSysuiStateFlags = sysUiStateFlags;
        boolean isImeSwitcherButtonVisible =
                (sysUiStateFlags & SYSUI_STATE_IME_SWITCHER_BUTTON_VISIBLE) != 0;
        boolean isImeVisible = (sysUiStateFlags & SYSUI_STATE_IME_VISIBLE) != 0;
        boolean isBackDismissIme = (sysUiStateFlags & SYSUI_STATE_BACK_DISMISS_IME) != 0;
        boolean a11yVisible = (sysUiStateFlags & SYSUI_STATE_A11Y_BUTTON_CLICKABLE) != 0;
        boolean isHomeDisabled = (sysUiStateFlags & SYSUI_STATE_HOME_DISABLED) != 0;
        // TODO: b/409075366 - ensure this signal is correctly set for external displays.
        boolean isRecentsDisabled = mContext.isPrimaryDisplay()
                && (sysUiStateFlags & SYSUI_STATE_OVERVIEW_DISABLED) != 0;
        boolean isBackDisabled = (sysUiStateFlags & SYSUI_STATE_BACK_DISABLED) != 0;
        long shadeExpandedFlags = SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
                | SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
        boolean isNotificationShadeExpanded = (sysUiStateFlags & shadeExpandedFlags) != 0;
        boolean isScreenPinningActive = (sysUiStateFlags & SYSUI_STATE_SCREEN_PINNING) != 0;
        boolean isVoiceInteractionWindowShowing =
                (sysUiStateFlags & SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING) != 0;
        boolean isKeyboardShortcutHelperShowing =
                (sysUiStateFlags & SYSUI_STATE_SHORTCUT_HELPER_SHOWING) != 0;
        boolean splitAnimationRunning =
                (sysUiStateFlags & SYSUI_STATE_DISABLE_GESTURE_SPLIT_INVOCATION) != 0;
        updateStateForFlag(FLAG_IME_SWITCHER_BUTTON_VISIBLE, isImeSwitcherButtonVisible);
        updateStateForFlag(FLAG_IME_VISIBLE, isImeVisible);
        updateStateForFlag(FLAG_BACK_DISMISS_IME, isBackDismissIme);
        updateStateForFlag(FLAG_A11Y_VISIBLE, a11yVisible);
        updateStateForFlag(FLAG_DISABLE_HOME, isHomeDisabled);
        updateStateForFlag(FLAG_DISABLE_RECENTS, isRecentsDisabled);
        updateStateForFlag(FLAG_DISABLE_BACK, isBackDisabled);
        updateStateForFlag(FLAG_NOTIFICATION_SHADE_EXPANDED, isNotificationShadeExpanded);
        updateStateForFlag(FLAG_SCREEN_PINNING_ACTIVE, isScreenPinningActive);
        updateStateForFlag(FLAG_VOICE_INTERACTION_WINDOW_SHOWING, isVoiceInteractionWindowShowing);
        updateStateForFlag(FLAG_KEYBOARD_SHORTCUT_HELPER_SHOWING, isKeyboardShortcutHelperShowing);

        if (mA11yButton != null) {
            // Only used in 3 button
            boolean a11yLongClickable =
                    (sysUiStateFlags & SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE) != 0;
            mA11yButton.setLongClickable(a11yLongClickable);
            updateButtonLayoutSpacing();
        }

        if (mNavButtonContainer.getChildCount() > 0) {
            for (int i = 0; i < mNavButtonContainer.getChildCount(); i++) {
                mNavButtonContainer.getChildAt(i).setEnabled(!splitAnimationRunning);
            }
        }
    }

    public void updateStateForSysuiFlags(@SystemUiStateFlags long systemUiStateFlags,
            boolean skipAnim) {
        if (systemUiStateFlags == mSysuiStateFlags) {
            return;
        }
        parseSystemUiFlags(systemUiStateFlags);
        applyState();
        if (skipAnim) {
            mPropertyHolders.forEach(StatePropertyHolder::endAnimation);
        }
    }

    /**
     * @return {@code true} if A11y is showing in 3 button nav taskbar
     */
    private boolean isA11yButtonPersistent() {
        return mContext.isThreeButtonNav() && (mState & FLAG_A11Y_VISIBLE) != 0;
    }

    /**
     * Should be called when we need to show back button for bouncer
     */
    public void setBackForBouncer(boolean isBouncerVisible) {
        updateStateForFlag(FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE, isBouncerVisible);
        applyState();
    }

    /**
     * Slightly misnamed, but should be called when keyguard OR AOD is showing.
     * We consider keyguardVisible when it's showing bouncer OR is occlucded by another app
     */
    public void setKeyguardVisible(boolean isKeyguardVisible, boolean isKeyguardOccluded) {
        updateStateForFlag(FLAG_KEYGUARD_VISIBLE, isKeyguardVisible || isKeyguardOccluded);
        updateStateForFlag(FLAG_KEYGUARD_OCCLUDED, isKeyguardOccluded);
        applyState();
    }

    /** {@code true} if a slide in view is currently visible over taskbar. */
    public void setSlideInViewVisible(boolean isSlideInViewVisible) {
        updateStateForFlag(FLAG_SLIDE_IN_VIEW_VISIBLE, isSlideInViewVisible);
        applyState();
    }

    /**
     * Returns true if IME bar is visible
     */
    public boolean isImeVisible() {
        return (mState & FLAG_IME_VISIBLE) != 0;
    }

    public boolean isImeRenderingNavButtons() {
        return mIsImeRenderingNavButtons;
    }

    /**
     * Returns true if the home button is disabled
     */
    public boolean isHomeDisabled() {
        return (mState & FLAG_DISABLE_HOME) != 0;
    }

    /**
     * Returns true if the recents (overview) button is disabled
     */
    public boolean isRecentsDisabled() {
        // TODO: b/409075366 - ensure this signal is correctly set for external displays.
        return (mState & FLAG_DISABLE_RECENTS) != 0 && mContext.isPrimaryDisplay();
    }

    /**
     * Adds the bounds corresponding to all visible buttons to provided region
     */
    public void addVisibleButtonsRegion(BaseDragLayer<?> parent, Region outRegion) {
        int count = mAllButtons.size();
        for (int i = 0; i < count; i++) {
            View button = mAllButtons.get(i);
            if (button.getVisibility() == View.VISIBLE) {
                parent.getDescendantRectRelativeToSelf(button, mTempRect);
                if (mHitboxExtender.extendedHitboxEnabled()) {
                    mTempRect.bottom += mContext.getDeviceProfile().getTaskbarOffsetY();
                }
                outRegion.op(mTempRect, Op.UNION);
            }
        }
    }

    /**
     * Returns multi-value alpha controller for back button.
     */
    public MultiValueAlpha getBackButtonAlpha() {
        return mBackButtonAlpha;
    }

    /**
     * Returns multi-value alpha controller for home button.
     */
    public MultiValueAlpha getHomeButtonAlpha() {
        return mHomeButtonAlpha;
    }

    /**
     * Sets the AccessibilityDelegate for the home button.
     */
    public void setHomeButtonAccessibilityDelegate(AccessibilityDelegate accessibilityDelegate) {
        if (mHomeButton == null) {
            return;
        }
        mHomeButton.setAccessibilityDelegate(accessibilityDelegate);
    }

    /**
     * Sets the AccessibilityDelegate for the back button.
     *
     * When setting a back button accessibility delegate, make sure to not dispatch any duplicate
     * click events. Click events get injected in the internal accessibility delegate in
     * {@link #setupBackButtonAccessibility(View, AccessibilityDelegate)}.
     */
    public void setBackButtonAccessibilityDelegate(AccessibilityDelegate accessibilityDelegate) {
        if (mBackButton == null) {
            return;
        }
        boolean predictiveBackThreeButtonNav;
        try {
            predictiveBackThreeButtonNav = predictiveBackThreeButtonNav();
        } catch (Throwable t) {
            predictiveBackThreeButtonNav = false;
        }
        if (predictiveBackThreeButtonNav) {
            setupBackButtonAccessibility(mBackButton, accessibilityDelegate);
        } else {
            mBackButton.setAccessibilityDelegate(accessibilityDelegate);
        }
    }

    public void setWallpaperVisible(boolean isVisible) {
        if (mContext.isPhoneMode()) {
            mTaskbarTransitions.setWallpaperVisibility(isVisible);
        }
    }

    public void onTransitionModeUpdated(int barMode, boolean checkBarModes) {
        mTransitionMode = barMode;
        if (checkBarModes) {
            checkNavBarModes();
        }
    }

    public void checkNavBarModes() {
        if (mContext.isPhoneMode()) {
            boolean isBarHidden = (mSysuiStateFlags & SYSUI_STATE_NAV_BAR_HIDDEN) != 0;
            mTaskbarTransitions.transitionTo(mTransitionMode, !isBarHidden);
        }
    }

    public void finishBarAnimations() {
        if (mContext.isPhoneMode()) {
            mTaskbarTransitions.finishAnimations();
        }
    }

    public void touchAutoDim(boolean reset) {
        if (mContext.isPhoneMode()) {
            mTaskbarTransitions.setAutoDim(false);
            mHandler.removeCallbacks(mAutoDim);
            if (reset) {
                mHandler.postDelayed(mAutoDim, AUTODIM_TIMEOUT_MS);
            }
        }
    }

    public void transitionTo(@BarTransitions.TransitionMode int barMode, boolean animate) {
        if (mContext.isPhoneMode()) {
            mTaskbarTransitions.transitionTo(barMode, animate);
        }
    }

    /** Use to set the translationY for the all nav+contextual buttons */
    public AnimatedFloat getTaskbarNavButtonTranslationY() {
        return mTaskbarNavButtonTranslationY;
    }

    /** Use to set the translationY for the all nav+contextual buttons when in Launcher */
    public AnimatedFloat getTaskbarNavButtonTranslationYForInAppDisplay() {
        return mTaskbarNavButtonTranslationYForInAppDisplay;
    }

    /** Use to set the dark intensity for the all nav+contextual buttons */
    public AnimatedFloat getTaskbarNavButtonDarkIntensity() {
        return mTaskbarNavButtonDarkIntensity;
    }

    /** Use to override the nav button color with {@link #mOnBackgroundIconColor}. */
    public AnimatedFloat getOnTaskbarBackgroundNavButtonColorOverride() {
        return mOnTaskbarBackgroundNavButtonColorOverride;
    }

    /**
     * Does not call {@link #applyState()}. Don't forget to!
     */
    private void updateStateForFlag(int flag, boolean enabled) {
        if (enabled) {
            mState |= flag;
        } else {
            mState &= ~flag;
        }
    }

    private void applyState() {
        int count = mPropertyHolders.size();
        for (int i = 0; i < count; i++) {
            mPropertyHolders.get(i).setState(mState, mContext.isGestureNav());
        }
    }

    private void updateNavButtonInAppDisplayProgressForSysui() {
        TaskbarUIController uiController = mControllers.uiController;
        if (uiController instanceof LauncherTaskbarUIController) {
            ((LauncherTaskbarUIController) uiController).onTaskbarInAppDisplayProgressUpdate(
                    mNavButtonInAppDisplayProgressForSysui.value, SYSUI_SURFACE_PROGRESS_INDEX);
        }
    }

    /**
     * Sets the translationY of the nav buttons based on the current device state.
     */
    public void updateNavButtonTranslationY() {
        if (mContext.isPhoneButtonNavMode()) {
            return;
        }
        final float normalTranslationY = mTaskbarNavButtonTranslationY.value;
        final float imeAdjustmentTranslationY = mTaskbarNavButtonTranslationYForIme.value;
        TaskbarUIController uiController = mControllers.uiController;
        final float inAppDisplayAdjustmentTranslationY =
                (uiController instanceof LauncherTaskbarUIController
                        && ((LauncherTaskbarUIController) uiController).shouldUseInAppLayout())
                        ? mTaskbarNavButtonTranslationYForInAppDisplay.value : 0;

        mLastSetNavButtonTranslationY = normalTranslationY
                + imeAdjustmentTranslationY
                + inAppDisplayAdjustmentTranslationY;
        mNavButtonsView.setTranslationY(mLastSetNavButtonTranslationY);
    }

    /**
     * Sets Taskbar 3-button mode icon colors based on the
     * {@link #mTaskbarNavButtonDarkIntensity} value piped in from Framework. For certain cases
     * in large screen taskbar where there may be opaque surfaces, the selected SystemUI button
     * colors are intentionally overridden.
     * <p>
     * This method is also called when any of the AnimatedFloat instances change.
     */
    private void updateNavButtonColor() {
        final ArgbEvaluator argbEvaluator = ArgbEvaluator.getInstance();
        int taskbarNavButtonColor = getSysUiIconColorOnHome(argbEvaluator);
        // Only phone mode foldable button colors should be identical to SysUI navbar colors.
        if (!(ENABLE_TASKBAR_NAVBAR_UNIFICATION && mContext.isPhoneMode())) {
            taskbarNavButtonColor = getTaskbarButtonColor(argbEvaluator, taskbarNavButtonColor);
        }
        applyButtonColors(taskbarNavButtonColor);
    }

    /**
     * Taskbar 3-button mode icon colors based on the
     * {@link #mTaskbarNavButtonDarkIntensity} value piped in from Framework.
     */
    private int getSysUiIconColorOnHome(ArgbEvaluator argbEvaluator) {
        return (int) argbEvaluator.evaluate(getTaskbarNavButtonDarkIntensity().value,
                mLightIconColorOnWorkspace, mDarkIconColorOnWorkspace);
    }

    /**
     * If Taskbar background is opaque or slide in overlay is visible, the selected SystemUI button
     * colors are intentionally overridden. The override can be disabled when
     * {@link #mOnBackgroundNavButtonColorOverrideMultiplier} is {@code 0}.
     */
    private int getTaskbarButtonColor(ArgbEvaluator argbEvaluator, int sysUiIconColorOnHome) {
        final float sysUIColorOverride =
                mOnBackgroundNavButtonColorOverrideMultiplier.value * Math.max(
                        mOnTaskbarBackgroundNavButtonColorOverride.value,
                        mSlideInViewVisibleNavButtonColorOverride.value);
        return (int) argbEvaluator.evaluate(sysUIColorOverride, sysUiIconColorOnHome,
                mOnBackgroundIconColor);
    }

    /**
     * Iteratively sets button colors for each button in {@link #mAllButtons}.
     */
    private void applyButtonColors(int iconColor) {
        for (ImageView button : mAllButtons) {
            button.setImageTintList(ColorStateList.valueOf(iconColor));
            Drawable background = button.getBackground();
            if (background instanceof KeyButtonRipple) {
                ((KeyButtonRipple) background).setDarkIntensity(
                        getTaskbarNavButtonDarkIntensity().value);
            }
        }
    }

    /**
     * Updates Taskbar 3-Button icon colors as {@link #mTaskbarNavButtonDarkIntensity} changes.
     */
    private void onDarkIntensityChanged() {
        updateNavButtonColor();
        if (mContext.isPhoneMode()) {
            mTaskbarTransitions.onDarkIntensityChanged(getTaskbarNavButtonDarkIntensity().value);
        }
    }

    protected ImageView addButton(@DrawableRes int drawableId, @TaskbarButton int buttonType,
            ViewGroup parent, TaskbarNavButtonController navButtonController, @IdRes int id) {
        return addButton(drawableId, buttonType, parent, navButtonController, id,
                R.layout.taskbar_nav_button);
    }

    @SuppressLint("ClickableViewAccessibility")
    private ImageView addButton(@DrawableRes int drawableId, @TaskbarButton int buttonType,
            ViewGroup parent, TaskbarNavButtonController navButtonController, @IdRes int id,
            @LayoutRes int layoutId) {
        ImageView buttonView = addButton(parent, id, layoutId);
        buttonView.setImageResource(drawableId);
        buttonView.setContentDescription(parent.getContext().getString(
                navButtonController.getButtonContentDescription(buttonType)));
        boolean predictiveBackThreeButtonNav;
        try {
            predictiveBackThreeButtonNav = predictiveBackThreeButtonNav();
        } catch (Throwable t) {
            predictiveBackThreeButtonNav = false;
        }
        if (predictiveBackThreeButtonNav && buttonType == BUTTON_BACK) {
            // set up special touch listener for back button to support predictive back
            setupBackButtonAccessibility(buttonView, null);
            setBackButtonTouchListener(buttonView, navButtonController);
            // Set this View clickable, so that NearestTouchFrame.java forwards closeby touches to
            // this View
            buttonView.setClickable(true);
        } else {
            buttonView.setOnClickListener(view ->
                    navButtonController.onButtonClick(buttonType, view));
            buttonView.setOnLongClickListener(view ->
                    navButtonController.onButtonLongClick(buttonType, view));
            buttonView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                }
                return false;
            });
        }
        return buttonView;
    }

    private void setupBackButtonAccessibility(View backButton,
            AccessibilityDelegate accessibilityDelegate) {
        View.AccessibilityDelegate backButtonAccessibilityDelegate =
                new View.AccessibilityDelegate() {
                    @Override
                    public boolean performAccessibilityAction(View host, int action, Bundle args) {
                        if (accessibilityDelegate != null) {
                            accessibilityDelegate.performAccessibilityAction(host, action, args);
                        }
                        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                            mControllers.navButtonController.sendBackKeyEvent(KeyEvent.ACTION_DOWN,
                                    /*cancelled*/ false);
                            mControllers.navButtonController.sendBackKeyEvent(KeyEvent.ACTION_UP,
                                    /*cancelled*/ false);
                            return true;
                        }
                        return super.performAccessibilityAction(host, action, args);
                    }
                };
        backButton.setAccessibilityDelegate(backButtonAccessibilityDelegate);
    }

    private void setBackButtonTouchListener(View buttonView,
            TaskbarNavButtonController navButtonController) {
        final RectF rect = new RectF();
        final AtomicBoolean hasSentDownEvent = new AtomicBoolean(false);
        final Runnable longPressTimeout = () -> {
            navButtonController.sendBackKeyEvent(KeyEvent.ACTION_DOWN, /*cancelled*/ false);
            hasSentDownEvent.set(true);
        };
        buttonView.setOnTouchListener((v, event) -> {
            int motionEventAction = event.getAction();
            if (motionEventAction == MotionEvent.ACTION_DOWN) {
                hasSentDownEvent.set(false);
                mHandler.postDelayed(longPressTimeout, PREDICTIVE_BACK_TIMEOUT_MS);
                rect.set(0, 0, v.getWidth(), v.getHeight());
                buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }
            boolean isCancelled = motionEventAction == MotionEvent.ACTION_CANCEL
                    || (!rect.contains(event.getX(), event.getY())
                    && (motionEventAction == MotionEvent.ACTION_MOVE
                    || motionEventAction == MotionEvent.ACTION_UP));
            if (motionEventAction != MotionEvent.ACTION_UP && !isCancelled) {
                // return early. we don't care about any other cases than UP or CANCEL from here on
                return false;
            }
            mHandler.removeCallbacks(longPressTimeout);
            if (!hasSentDownEvent.get()) {
                if (isCancelled) {
                    // if it is cancelled and ACTION_DOWN has not been sent yet, return early and
                    // don't send anything to sysui.
                    return false;
                }
                navButtonController.sendBackKeyEvent(KeyEvent.ACTION_DOWN, isCancelled);
            }
            navButtonController.sendBackKeyEvent(KeyEvent.ACTION_UP, isCancelled);
            if (motionEventAction == MotionEvent.ACTION_UP && !isCancelled) {
                buttonView.performClick();
            }
            return false;
        });
        buttonView.setOnLongClickListener((view) ->  {
            navButtonController.onButtonLongClick(BUTTON_BACK, view);
            return false;
        });
    }

    private ImageView addButton(ViewGroup parent, @IdRes int id, @LayoutRes int layoutId) {
        ImageView buttonView = (ImageView) mContext.getLayoutInflater()
                .inflate(layoutId, parent, false);
        buttonView.setId(id);
        parent.addView(buttonView);
        mAllButtons.add(buttonView);
        return buttonView;
    }

    public boolean isEventOverAnyItem(MotionEvent ev) {
        return mFloatingRotationButtonBounds.contains((int) ev.getX(), (int) ev.getY());
    }

    public void onConfigurationChanged(@Config int configChanges) {
        if (mFloatingRotationButton != null) {
            mFloatingRotationButton.onConfigurationChanged(configChanges);
        }
        if (!mContext.isUserSetupComplete() && !ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
            handleSetupUi();
        }
        updateButtonLayoutSpacing();
    }

    private void handleSetupUi() {
        // Setup wizard handles the UI when the expressive theme is enabled and Simple View isn't.
        if (mIsExpressiveThemeEnabled && !mContext.isSimpleViewEnabled()) {
            return;
        }
        // Since setup wizard only has back button enabled, it looks strange to be
        // end-aligned, so start-align instead.
        FrameLayout.LayoutParams navButtonsLayoutParams = (FrameLayout.LayoutParams)
                mNavButtonContainer.getLayoutParams();
        FrameLayout.LayoutParams navButtonsViewLayoutParams = (FrameLayout.LayoutParams)
                mNavButtonsView.getLayoutParams();
        Resources resources = mContext.getResources();
        DeviceProfile deviceProfile = mContext.getDeviceProfile();

        navButtonsLayoutParams.setMarginEnd(0);
        navButtonsLayoutParams.gravity = Gravity.START;
        mControllers.taskbarActivityContext.setTaskbarWindowSize(
                mControllers.taskbarActivityContext.getSetupWindowSize());

        // If SUW is on a large screen device that is landscape (or has a square aspect
        // ratio) the back button has to be placed accordingly
        if ((deviceProfile.getDeviceProperties().isTablet() && deviceProfile.getDeviceProperties().isLandscape())
                || (deviceProfile.getDeviceProperties().getAspectRatio() > SQUARE_ASPECT_RATIO_BOTTOM_BOUND
                && deviceProfile.getDeviceProperties().getAspectRatio() < SQUARE_ASPECT_RATIO_UPPER_BOUND)) {
            navButtonsLayoutParams.setMarginStart(
                    resources.getDimensionPixelSize(R.dimen.taskbar_back_button_suw_start_margin));
            navButtonsViewLayoutParams.bottomMargin = resources.getDimensionPixelSize(
                    R.dimen.taskbar_back_button_suw_bottom_margin);
            navButtonsLayoutParams.height = resources.getDimensionPixelSize(
                    R.dimen.taskbar_back_button_suw_height);
        } else {
            int phoneOrPortraitSetupMargin = resources.getDimensionPixelSize(
                    R.dimen.taskbar_contextual_button_suw_margin);
            navButtonsLayoutParams.setMarginStart(phoneOrPortraitSetupMargin);
            navButtonsLayoutParams.bottomMargin = !deviceProfile.getDeviceProperties().isLandscape()
                    ? 0
                    : phoneOrPortraitSetupMargin - (resources.getDimensionPixelSize(
                            R.dimen.taskbar_nav_buttons_size) / 2);
            navButtonsViewLayoutParams.height = resources.getDimensionPixelSize(
                    R.dimen.taskbar_contextual_button_suw_height);
        }
        mNavButtonsView.setLayoutParams(navButtonsViewLayoutParams);
        mNavButtonContainer.setLayoutParams(navButtonsLayoutParams);
    }

    /**
     * Adds the correct spacing to 3 button nav container depending on if device is in kids mode,
     * setup wizard, or normal 3 button nav.
     */
    private void updateButtonLayoutSpacing() {
        boolean isThreeButtonNav = mContext.isThreeButtonNav();

        DeviceProfile dp = mContext.getDeviceProfile();
        Resources res = mContext.getResources();
        boolean isInSetup = !mContext.isUserSetupComplete();
        // TODO(b/244231596) we're getting the incorrect kidsMode value in small-screen
        boolean isInKidsMode = mContext.isNavBarKidsModeActive();

        if (ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
            NavButtonLayoutter navButtonLayoutter =
                    NavButtonLayoutFactory.Companion.getUiLayoutter(
                            dp, mNavButtonsView, mImeSwitcherButton,
                            mA11yButton, mSpace, res, isInKidsMode, isInSetup, isThreeButtonNav,
                            mContext.isPhoneMode(), mWindowManagerProxy.getRotation(mContext));
            navButtonLayoutter.layoutButtons(mContext, isA11yButtonPersistent());
            updateButtonsBackground();
            updateNavButtonColor();
            return;
        }

        if (isInSetup) {
            handleSetupUi();
        } else if (isInKidsMode) {
            int iconSize = res.getDimensionPixelSize(
                    R.dimen.taskbar_icon_size_kids);
            int buttonWidth = res.getDimensionPixelSize(
                    R.dimen.taskbar_nav_buttons_width_kids);
            int buttonHeight = res.getDimensionPixelSize(
                    R.dimen.taskbar_nav_buttons_height_kids);
            int buttonRadius = res.getDimensionPixelSize(
                    R.dimen.taskbar_nav_buttons_corner_radius_kids);
            int paddingleft = (buttonWidth - iconSize) / 2;
            int paddingRight = paddingleft;
            int paddingTop = (buttonHeight - iconSize) / 2;
            int paddingBottom = paddingTop;

            // Update icons
            final RotateDrawable rotateDrawable = new RotateDrawable();
            rotateDrawable.setDrawable(mContext.getDrawable(R.drawable.ic_sysbar_back_kids));
            rotateDrawable.setFromDegrees(0f);
            rotateDrawable.setToDegrees(-90f);
            mBackButton.setImageDrawable(rotateDrawable);
            mBackButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mBackButton.setPadding(paddingleft, paddingTop, paddingRight, paddingBottom);

            mHomeButton.setImageDrawable(
                    mHomeButton.getContext().getDrawable(R.drawable.ic_sysbar_home_kids));
            mHomeButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mHomeButton.setPadding(paddingleft, paddingTop, paddingRight, paddingBottom);

            // Home button layout
            LinearLayout.LayoutParams homeLayoutparams = new LinearLayout.LayoutParams(
                    buttonWidth,
                    buttonHeight
            );
            int homeButtonLeftMargin = res.getDimensionPixelSize(
                    R.dimen.taskbar_home_button_left_margin_kids);
            homeLayoutparams.setMargins(homeButtonLeftMargin, 0, 0, 0);
            mHomeButton.setLayoutParams(homeLayoutparams);

            // Back button layout
            LinearLayout.LayoutParams backLayoutParams = new LinearLayout.LayoutParams(
                    buttonWidth,
                    buttonHeight
            );
            int backButtonLeftMargin = res.getDimensionPixelSize(
                    R.dimen.taskbar_back_button_left_margin_kids);
            backLayoutParams.setMargins(backButtonLeftMargin, 0, 0, 0);
            mBackButton.setLayoutParams(backLayoutParams);

            // Button backgrounds
            int whiteWith10PctAlpha = Color.argb(0.1f, 1, 1, 1);
            PaintDrawable buttonBackground = new PaintDrawable(whiteWith10PctAlpha);
            buttonBackground.setCornerRadius(buttonRadius);
            mHomeButton.setBackground(buttonBackground);
            mBackButton.setBackground(buttonBackground);

            // Update alignment within taskbar
            FrameLayout.LayoutParams navButtonsLayoutParams = (FrameLayout.LayoutParams)
                    mNavButtonContainer.getLayoutParams();
            navButtonsLayoutParams.setMarginStart(
                    navButtonsLayoutParams.getMarginEnd() / 2);
            navButtonsLayoutParams.setMarginEnd(navButtonsLayoutParams.getMarginStart());
            navButtonsLayoutParams.gravity = Gravity.CENTER;
            mNavButtonContainer.requestLayout();

            mHomeButton.setOnLongClickListener(null);
        } else if (isThreeButtonNav) {
            final RotateDrawable rotateDrawable = new RotateDrawable();
            rotateDrawable.setDrawable(mContext.getDrawable(R.drawable.ic_sysbar_back));
            rotateDrawable.setFromDegrees(0f);
            rotateDrawable.setToDegrees(Utilities.isRtl(mContext.getResources()) ? 90f : -90f);
            mBackButton.setImageDrawable(rotateDrawable);

            // Setup normal 3 button
            // Add spacing after the end of the last nav button
            FrameLayout.LayoutParams navButtonParams =
                    (FrameLayout.LayoutParams) mNavButtonContainer.getLayoutParams();
            navButtonParams.gravity = Gravity.END;
            navButtonParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            navButtonParams.height = MATCH_PARENT;

            int navMarginEnd = (int) res.getDimension(dp.inv.inlineNavButtonsEndSpacing);
            int contextualWidth = mEndContextualContainer.getWidth();
            // If contextual buttons are showing, we check if the end margin is enough for the
            // contextual button to be showing - if not, move the nav buttons over a smidge
            if (isA11yButtonPersistent() && navMarginEnd < contextualWidth) {
                // Additional spacing, eat up half of space between last icon and nav button
                navMarginEnd += res.getDimensionPixelSize(R.dimen.taskbar_hotseat_nav_spacing) / 2;
            }
            navButtonParams.setMarginEnd(navMarginEnd);
            mNavButtonContainer.setLayoutParams(navButtonParams);

            // Add the spaces in between the nav buttons
            int spaceInBetween = res.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween);
            for (int i = 0; i < mNavButtonContainer.getChildCount(); i++) {
                View navButton = mNavButtonContainer.getChildAt(i);
                LinearLayout.LayoutParams buttonLayoutParams =
                        (LinearLayout.LayoutParams) navButton.getLayoutParams();
                buttonLayoutParams.weight = 0;
                if (i == 0) {
                    buttonLayoutParams.setMarginEnd(spaceInBetween / 2);
                } else if (i == mNavButtonContainer.getChildCount() - 1) {
                    buttonLayoutParams.setMarginStart(spaceInBetween / 2);
                } else {
                    buttonLayoutParams.setMarginStart(spaceInBetween / 2);
                    buttonLayoutParams.setMarginEnd(spaceInBetween / 2);
                }
            }
        }
    }

    private void updateButtonsBackground() {
        boolean clipped = !mContext.isPhoneButtonNavMode();
        mNavButtonContainer.setClipToPadding(clipped);
        mNavButtonContainer.setClipChildren(clipped);
        mNavButtonsView.setClipToPadding(clipped);
        mNavButtonsView.setClipChildren(clipped);

        for (ImageView button : mAllButtons) {
            updateButtonBackground(button, mContext.isPhoneButtonNavMode());
        }
    }

    private static void updateButtonBackground(View view, boolean isPhoneButtonNavMode) {
        if (isPhoneButtonNavMode) {
            view.setBackground(new KeyButtonRipple(view.getContext(), view,
                    R.dimen.key_button_ripple_max_width));
        } else {
            view.setBackgroundResource(R.drawable.taskbar_icon_click_feedback_roundrect);
        }
    }

    public void onDestroy() {
        mPropertyHolders.clear();
        if (mFloatingRotationButton != null) {
            mFloatingRotationButton.hide();
            mFloatingRotationButton = null;
        }

        moveNavButtonsBackToTaskbarWindow();
        mNavButtonContainer.removeAllViews();
        mEndContextualContainer.removeAllViews();
        mStartContextualContainer.removeAllViews();
        mAllButtons.clear();
    }

    /**
     * Moves mNavButtonsView from TaskbarDragLayer to a placeholder BaseDragLayer on a new window.
     */
    public void moveNavButtonsToNewWindow() {
        if (mAreNavButtonsInSeparateWindow) {
            return;
        }

        if (mIsImeRenderingNavButtons) {
            // IME is rendering the nav buttons, so we don't need to create a new layer for them.
            return;
        }

        mSeparateWindowParent.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                mSeparateWindowParent.getViewTreeObserver().addOnComputeInternalInsetsListener(
                        mSeparateWindowInsetsComputer);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                mSeparateWindowParent.removeOnAttachStateChangeListener(this);
                mSeparateWindowParent.getViewTreeObserver().removeOnComputeInternalInsetsListener(
                        mSeparateWindowInsetsComputer);
            }
        });

        mAreNavButtonsInSeparateWindow = true;
        mContext.getDragLayer().removeView(mNavButtonsView);
        mSeparateWindowParent.addView(mNavButtonsView);
        WindowManager.LayoutParams windowLayoutParams = mContext.createDefaultWindowLayoutParams(
                TYPE_NAVIGATION_BAR_PANEL, NAV_BUTTONS_SEPARATE_WINDOW_TITLE);
        mContext.addWindowView(mSeparateWindowParent, windowLayoutParams);

    }

    /**
     * Moves mNavButtonsView from its temporary window and reattaches it to TaskbarDragLayer.
     */
    public void moveNavButtonsBackToTaskbarWindow() {
        if (!mAreNavButtonsInSeparateWindow) {
            return;
        }

        mAreNavButtonsInSeparateWindow = false;
        mContext.removeWindowView(mSeparateWindowParent);
        mSeparateWindowParent.removeView(mNavButtonsView);
        mContext.getDragLayer().addView(mNavButtonsView);
    }

    private void onComputeInsetsForSeparateWindow(ViewTreeObserver.InternalInsetsInfo insetsInfo) {
        addVisibleButtonsRegion(mSeparateWindowParent, insetsInfo.touchableRegion);
        insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
    }

    /**
     * Called whenever a new ui controller is set, and should update anything that depends on the
     * ui controller.
     */
    public void onUiControllerChanged() {
        updateNavButtonInAppDisplayProgressForSysui();
        updateNavButtonTranslationY();
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "NavbarButtonsViewController:");

        pw.println(prefix + "\tmState=" + getStateString(mState));
        pw.println(prefix + "\tmFloatingRotationButtonBounds=" + mFloatingRotationButtonBounds);
        pw.println(prefix + "\tmSysuiStateFlags=" + QuickStepContract.getSystemUiStateString(
                mSysuiStateFlags));
        pw.println(prefix + "\tLast set nav button translationY=" + mLastSetNavButtonTranslationY);
        pw.println(prefix + "\t\tmTaskbarNavButtonTranslationY="
                + mTaskbarNavButtonTranslationY.value);
        pw.println(prefix + "\t\tmTaskbarNavButtonTranslationYForInAppDisplay="
                + mTaskbarNavButtonTranslationYForInAppDisplay.value);
        pw.println(prefix + "\t\tmTaskbarNavButtonTranslationYForIme="
                + mTaskbarNavButtonTranslationYForIme.value);
        pw.println(prefix + "\t\tmTaskbarNavButtonDarkIntensity="
                + mTaskbarNavButtonDarkIntensity.value);
        pw.println(prefix + "\t\tmSlideInViewVisibleNavButtonColorOverride="
                + mSlideInViewVisibleNavButtonColorOverride.value);
        pw.println(prefix + "\t\tmOnTaskbarBackgroundNavButtonColorOverride="
                + mOnTaskbarBackgroundNavButtonColorOverride.value);
        pw.println(prefix + "\t\tmOnBackgroundNavButtonColorOverrideMultiplier="
                + mOnBackgroundNavButtonColorOverrideMultiplier.value);

        mNavButtonsView.dumpLogs(prefix + "\t", pw);
        if (mContext.isPhoneMode()) {
            mTaskbarTransitions.dumpLogs(prefix + "\t", pw);
        }
    }

    private static String getStateString(int flags) {
        StringJoiner str = new StringJoiner("|");
        appendFlag(str, flags, FLAG_IME_SWITCHER_BUTTON_VISIBLE,
                "FLAG_IME_SWITCHER_BUTTON_VISIBLE");
        appendFlag(str, flags, FLAG_IME_VISIBLE, "FLAG_IME_VISIBLE");
        appendFlag(str, flags, FLAG_BACK_DISMISS_IME, "FLAG_BACK_DISMISS_IME");
        appendFlag(str, flags, FLAG_A11Y_VISIBLE, "FLAG_A11Y_VISIBLE");
        appendFlag(str, flags, FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE,
                "FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE");
        appendFlag(str, flags, FLAG_KEYGUARD_VISIBLE, "FLAG_KEYGUARD_VISIBLE");
        appendFlag(str, flags, FLAG_KEYGUARD_OCCLUDED, "FLAG_KEYGUARD_OCCLUDED");
        appendFlag(str, flags, FLAG_DISABLE_HOME, "FLAG_DISABLE_HOME");
        appendFlag(str, flags, FLAG_DISABLE_RECENTS, "FLAG_DISABLE_RECENTS");
        appendFlag(str, flags, FLAG_DISABLE_BACK, "FLAG_DISABLE_BACK");
        appendFlag(str, flags, FLAG_NOTIFICATION_SHADE_EXPANDED,
                "FLAG_NOTIFICATION_SHADE_EXPANDED");
        appendFlag(str, flags, FLAG_SCREEN_PINNING_ACTIVE, "FLAG_SCREEN_PINNING_ACTIVE");
        appendFlag(str, flags, FLAG_VOICE_INTERACTION_WINDOW_SHOWING,
                "FLAG_VOICE_INTERACTION_WINDOW_SHOWING");
        appendFlag(str, flags, FLAG_KEYBOARD_SHORTCUT_HELPER_SHOWING,
                "FLAG_KEYBOARD_SHORTCUT_HELPER_SHOWING");
        return str.toString();
    }

    public TouchController getTouchController() {
        return mHitboxExtender;
    }

    /**
     * @param alignment 0 -> Taskbar, 1 -> Workspace
     */
    public void updateTaskbarAlignment(float alignment) {
        mHitboxExtender.onAnimationProgressToOverview(alignment);
    }

    /** Adjusts navigation buttons layout accordingly to the bubble bar position. */
    @Override
    public void onBubbleBarLocationUpdated(BubbleBarLocation location) {
        boolean locationUpdated = location != mBubbleBarTargetLocation;
        if (locationUpdated) {
            cancelExistingNavBarAnimation();
        } else {
            endExistingAnimation();
        }
        mNavButtonContainer.setTranslationX(getNavBarTranslationX(location));
        mBubbleBarTargetLocation = location;
    }

    /** Animates navigation buttons accordingly to the bubble bar position. */
    @Override
    public void onBubbleBarLocationAnimated(BubbleBarLocation location) {
        if (location == mBubbleBarTargetLocation) return;
        cancelExistingNavBarAnimation();
        mBubbleBarTargetLocation = location;
        int finalX = getNavBarTranslationX(location);
        Animator teleportAnimator = BarsLocationAnimatorHelper
                .getTeleportAnimatorForNavButtons(location, mNavButtonContainer, finalX);
        teleportAnimator.addListener(forEndCallback(() -> mNavBarLocationAnimator = null));
        mNavBarLocationAnimator = teleportAnimator;
        mNavBarLocationAnimator.start();
    }

    private void endExistingAnimation() {
        if (mNavBarLocationAnimator != null) {
            mNavBarLocationAnimator.end();
            mNavBarLocationAnimator = null;
        }
    }

    private void cancelExistingNavBarAnimation() {
        if (mNavBarLocationAnimator != null) {
            mNavBarLocationAnimator.cancel();
            mNavBarLocationAnimator = null;
        }
    }

    private int getNavBarTranslationX(BubbleBarLocation location) {
        boolean isNavbarOnRight = location.isOnLeft(mNavButtonsView.isLayoutRtl());
        DeviceProfile dp = mContext.getDeviceProfile();
        float navBarTargetStartX;
        if (!mContext.isUserSetupComplete()) {
            // Skip additional translations on the nav bar container while in SUW layout
            return 0;
        } else if (mContext.shouldStartAlignTaskbar()) {
            int navBarSpacing = dp.getHotseatProfile().getInlineNavButtonsEndSpacingPx();
            // If the taskbar is start aligned the navigation bar is aligned to the start or end of
            // the container, depending on the bubble bar location
            if (isNavbarOnRight) {
                navBarTargetStartX = dp.getDeviceProperties().getWidthPx() - navBarSpacing - mNavButtonContainer.getWidth();
            } else {
                navBarTargetStartX = navBarSpacing;
            }
        } else {
            // If the task bar is not start aligned, the navigation bar is located in the center
            // between the taskbar and screen edges, depending on the bubble bar location.
            float navbarWidth = mNavButtonContainer.getWidth();
            Rect taskbarBounds = mControllers.taskbarViewController
                    .getTransientTaskbarIconLayoutBoundsInParent();
            if (isNavbarOnRight) {
                if (mNavButtonsView.isLayoutRtl()) {
                    float taskBarEnd = taskbarBounds.right;
                    navBarTargetStartX = (dp.getDeviceProperties().getWidthPx() + taskBarEnd - navbarWidth) / 2;
                } else {
                    navBarTargetStartX = mNavButtonContainer.getLeft();
                }
            } else {
                float taskBarStart = taskbarBounds.left;
                navBarTargetStartX = (taskBarStart - navbarWidth) / 2;
            }
        }
        return (int) navBarTargetStartX - mNavButtonContainer.getLeft();
    }

    /** Adjusts the navigation buttons layout position according to the bubble bar location. */
    public void onLayoutsUpdated() {
        // no need to do anything if on phone, or if taskbar or navbar views were not placed on
        // screen.
        Rect transientTaskbarIconLayoutBoundsInParent = mControllers.taskbarViewController
                .getTransientTaskbarIconLayoutBoundsInParent();
        if (mContext.getDeviceProfile().getDeviceProperties().isPhone()
                || transientTaskbarIconLayoutBoundsInParent.isEmpty()
                || mNavButtonsView.getWidth() == 0) {
            return;
        }
        if (mControllers.bubbleControllers.isPresent()) {
            if (mBubbleBarTargetLocation == null) {
                // only set bubble bar location if it was not set before
                mBubbleBarTargetLocation = mControllers.bubbleControllers.get()
                        .bubbleBarViewController.getBubbleBarLocation();
            }
            onBubbleBarLocationUpdated(mBubbleBarTargetLocation);
        }
    }

    private class RotationButtonListener implements RotationButton.RotationButtonUpdatesCallback {
        @Override
        public void onVisibilityChanged(boolean isVisible) {
            if (isVisible) {
                mFloatingRotationButton.getCurrentView()
                        .getBoundsOnScreen(mFloatingRotationButtonBounds);
            } else {
                mFloatingRotationButtonBounds.setEmpty();
            }
        }
    }

    private static class StatePropertyHolder {

        private final float mEnabledValue, mDisabledValue;
        private final ObjectAnimator mAnimator;
        private final IntPredicate mEnableCondition;

        private boolean mIsEnabled = true;

        StatePropertyHolder(View view, IntPredicate enableCondition) {
            this(view, enableCondition, LauncherAnimUtils.VIEW_ALPHA, 1, 0);
            mAnimator.addListener(new AlphaUpdateListener(view));
        }

        StatePropertyHolder(MultiProperty alphaProperty,
                IntPredicate enableCondition) {
            this(alphaProperty, enableCondition, MULTI_PROPERTY_VALUE, 1, 0);
        }

        StatePropertyHolder(AnimatedFloat animatedFloat, IntPredicate enableCondition) {
            this(animatedFloat, enableCondition, AnimatedFloat.VALUE, 1, 0);
        }

        <T> StatePropertyHolder(T target, IntPredicate enabledCondition,
                Property<T, Float> property, float enabledValue, float disabledValue) {
            mEnableCondition = enabledCondition;
            mEnabledValue = enabledValue;
            mDisabledValue = disabledValue;
            mAnimator = ObjectAnimator.ofFloat(target, property, enabledValue, disabledValue);
        }

        public void setState(int flags, boolean skipAnimation) {
            boolean isEnabled = mEnableCondition.test(flags);
            if (mIsEnabled != isEnabled) {
                mIsEnabled = isEnabled;
                mAnimator.cancel();
                mAnimator.setFloatValues(mIsEnabled ? mEnabledValue : mDisabledValue);
                mAnimator.start();
                if (skipAnimation) {
                    mAnimator.end();
                }
            }
        }

        public void endAnimation() {
            if (mAnimator.isRunning()) {
                mAnimator.end();
            }
        }
    }
}
