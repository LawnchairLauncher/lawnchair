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

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_A11Y;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_BACK;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_HOME;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_IME_SWITCH;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_RECENTS;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_KEYGUARD;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BACK_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_SWITCHER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.DrawableRes;
import android.annotation.IdRes;
import android.annotation.LayoutRes;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.util.Property;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnHoverListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarButton;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.Themes;
import com.android.quickstep.AnimatedFloat;
import com.android.systemui.shared.rotation.FloatingRotationButton;
import com.android.systemui.shared.rotation.RotationButton;
import com.android.systemui.shared.rotation.RotationButtonController;

import java.util.ArrayList;
import java.util.function.IntPredicate;

/**
 * Controller for managing nav bar buttons in taskbar
 */
public class NavbarButtonsViewController {

    private final Rect mTempRect = new Rect();

    private static final int FLAG_SWITCHER_SUPPORTED = 1 << 0;
    private static final int FLAG_IME_VISIBLE = 1 << 1;
    private static final int FLAG_ROTATION_BUTTON_VISIBLE = 1 << 2;
    private static final int FLAG_A11Y_VISIBLE = 1 << 3;
    private static final int FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE = 1 << 4;
    private static final int FLAG_KEYGUARD_VISIBLE = 1 << 5;
    private static final int FLAG_KEYGUARD_OCCLUDED = 1 << 6;
    private static final int FLAG_DISABLE_HOME = 1 << 7;
    private static final int FLAG_DISABLE_RECENTS = 1 << 8;
    private static final int FLAG_DISABLE_BACK = 1 << 9;
    private static final int FLAG_NOTIFICATION_SHADE_EXPANDED = 1 << 10;

    private static final int MASK_IME_SWITCHER_VISIBLE = FLAG_SWITCHER_SUPPORTED | FLAG_IME_VISIBLE;

    private final ArrayList<StatePropertyHolder> mPropertyHolders = new ArrayList<>();
    private final ArrayList<ImageView> mAllButtons = new ArrayList<>();
    private int mState;

    private final TaskbarActivityContext mContext;
    private final FrameLayout mNavButtonsView;
    private final ViewGroup mNavButtonContainer;
    // Used for IME+A11Y buttons
    private final ViewGroup mEndContextualContainer;
    private final ViewGroup mStartContextualContainer;
    private final int mLightIconColor;
    private final int mDarkIconColor;

    private final AnimatedFloat mTaskbarNavButtonTranslationY = new AnimatedFloat(
            this::updateNavButtonTranslationY);
    private final AnimatedFloat mNavButtonTranslationYMultiplier = new AnimatedFloat(
            this::updateNavButtonTranslationY);
    private final AnimatedFloat mTaskbarNavButtonDarkIntensity = new AnimatedFloat(
            this::updateNavButtonDarkIntensity);
    private final AnimatedFloat mNavButtonDarkIntensityMultiplier = new AnimatedFloat(
            this::updateNavButtonDarkIntensity);
    private final RotationButtonListener mRotationButtonListener = new RotationButtonListener();

    private final Rect mFloatingRotationButtonBounds = new Rect();

    // Initialized in init.
    private TaskbarControllers mControllers;
    private View mA11yButton;
    private int mSysuiStateFlags;
    private View mBackButton;
    private FloatingRotationButton mFloatingRotationButton;

    public NavbarButtonsViewController(TaskbarActivityContext context, FrameLayout navButtonsView) {
        mContext = context;
        mNavButtonsView = navButtonsView;
        mNavButtonContainer = mNavButtonsView.findViewById(R.id.end_nav_buttons);
        mEndContextualContainer = mNavButtonsView.findViewById(R.id.end_contextual_buttons);
        mStartContextualContainer = mNavButtonsView.findViewById(R.id.start_contextual_buttons);

        mLightIconColor = context.getColor(R.color.taskbar_nav_icon_light_color);
        mDarkIconColor = context.getColor(R.color.taskbar_nav_icon_dark_color);
    }

    /**
     * Initializes the controller
     */
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        mNavButtonsView.getLayoutParams().height = mContext.getDeviceProfile().taskbarSize;
        mNavButtonTranslationYMultiplier.value = 1;

        boolean isThreeButtonNav = mContext.isThreeButtonNav();
        // IME switcher
        View imeSwitcherButton = addButton(R.drawable.ic_ime_switcher, BUTTON_IME_SWITCH,
                isThreeButtonNav ? mStartContextualContainer : mEndContextualContainer,
                mControllers.navButtonController, R.id.ime_switcher);
        mPropertyHolders.add(new StatePropertyHolder(imeSwitcherButton,
                flags -> ((flags & MASK_IME_SWITCHER_VISIBLE) == MASK_IME_SWITCHER_VISIBLE)
                        && ((flags & FLAG_ROTATION_BUTTON_VISIBLE) == 0)));

        mPropertyHolders.add(new StatePropertyHolder(
                mControllers.taskbarViewController.getTaskbarIconAlpha()
                        .getProperty(ALPHA_INDEX_KEYGUARD),
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0, MultiValueAlpha.VALUE, 1, 0));

        mPropertyHolders.add(new StatePropertyHolder(mControllers.taskbarDragLayerController
                .getKeyguardBgTaskbar(),
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0, AnimatedFloat.VALUE, 1, 0));

        // Make sure to remove nav bar buttons translation when notification shade is expanded.
        mPropertyHolders.add(new StatePropertyHolder(mNavButtonTranslationYMultiplier,
                flags -> (flags & FLAG_NOTIFICATION_SHADE_EXPANDED) != 0, AnimatedFloat.VALUE,
                0, 1));

        // Force nav buttons (specifically back button) to be visible during setup wizard.
        boolean isInSetup = !mContext.isUserSetupComplete();
        if (isThreeButtonNav || isInSetup) {
            initButtons(mNavButtonContainer, mEndContextualContainer,
                    mControllers.navButtonController);

            if (isInSetup) {
                // Since setup wizard only has back button enabled, it looks strange to be
                // end-aligned, so start-align instead.
                FrameLayout.LayoutParams navButtonsLayoutParams = (FrameLayout.LayoutParams)
                        mNavButtonContainer.getLayoutParams();
                navButtonsLayoutParams.setMarginStart(navButtonsLayoutParams.getMarginEnd());
                navButtonsLayoutParams.setMarginEnd(0);
                navButtonsLayoutParams.gravity = Gravity.START;
                mNavButtonContainer.requestLayout();

                if (!isThreeButtonNav) {
                    // Tint all the nav buttons since there's no taskbar background in SUW.
                    for (int i = 0; i < mNavButtonContainer.getChildCount(); i++) {
                        if (!(mNavButtonContainer.getChildAt(i) instanceof ImageView)) continue;
                        ImageView button = (ImageView) mNavButtonContainer.getChildAt(i);
                        button.setImageTintList(ColorStateList.valueOf(Themes.getAttrColor(
                                button.getContext(), android.R.attr.textColorPrimary)));
                    }
                }
            }

            // Animate taskbar background when any of these flags are enabled
            int flagsToShowBg = FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE
                    | FLAG_NOTIFICATION_SHADE_EXPANDED;
            mPropertyHolders.add(new StatePropertyHolder(
                    mControllers.taskbarDragLayerController.getNavbarBackgroundAlpha(),
                    flags -> (flags & flagsToShowBg) != 0, AnimatedFloat.VALUE, 1, 0));

            // Rotation button
            RotationButton rotationButton = new RotationButtonImpl(
                    addButton(mEndContextualContainer, R.id.rotate_suggestion,
                            R.layout.taskbar_contextual_button));
            rotationButton.hide();
            mControllers.rotationButtonController.setRotationButton(rotationButton, null);
        } else {
            mFloatingRotationButton = new FloatingRotationButton(mContext,
                    R.string.accessibility_rotate_button,
                    R.layout.rotate_suggestion,
                    R.id.rotate_suggestion,
                    R.dimen.floating_rotation_button_min_margin,
                    R.dimen.rounded_corner_content_padding,
                    R.dimen.floating_rotation_button_taskbar_left_margin,
                    R.dimen.floating_rotation_button_taskbar_bottom_margin,
                    R.dimen.floating_rotation_button_diameter,
                    R.dimen.key_button_ripple_max_width);
            mControllers.rotationButtonController.setRotationButton(mFloatingRotationButton,
                    mRotationButtonListener);

            View imeDownButton = addButton(R.drawable.ic_sysbar_back, BUTTON_BACK,
                    mStartContextualContainer, mControllers.navButtonController, R.id.back);
            imeDownButton.setRotation(Utilities.isRtl(mContext.getResources()) ? 90 : -90);
            // Rotate when Ime visible
            mPropertyHolders.add(new StatePropertyHolder(imeDownButton,
                    flags -> (flags & FLAG_IME_VISIBLE) != 0));
        }

        applyState();
        mPropertyHolders.forEach(StatePropertyHolder::endAnimation);
    }

    private void initButtons(ViewGroup navContainer, ViewGroup endContainer,
            TaskbarNavButtonController navButtonController) {

        mBackButton = addButton(R.drawable.ic_sysbar_back, BUTTON_BACK,
                mNavButtonContainer, mControllers.navButtonController, R.id.back);
        mPropertyHolders.add(new StatePropertyHolder(mBackButton,
                flags -> (flags & FLAG_DISABLE_BACK) == 0));
        boolean isRtl = Utilities.isRtl(mContext.getResources());
        mPropertyHolders.add(new StatePropertyHolder(
                mBackButton, flags -> (flags & FLAG_IME_VISIBLE) != 0, View.ROTATION,
                isRtl ? 90 : -90, 0));
        // Hide when keyguard is showing, show when bouncer or lock screen app is showing
        mPropertyHolders.add(new StatePropertyHolder(mBackButton,
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0 ||
                        (flags & FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE) != 0 ||
                        (flags & FLAG_KEYGUARD_OCCLUDED) != 0));
        // Translate back button to be at end/start of other buttons for keyguard
        int navButtonSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.taskbar_nav_buttons_size);
        mPropertyHolders.add(new StatePropertyHolder(
                mBackButton, flags -> (flags & FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE) != 0
                        || (flags & FLAG_KEYGUARD_VISIBLE) != 0,
                VIEW_TRANSLATE_X, navButtonSize * (isRtl ? -2 : 2), 0));


        // home and recents buttons
        View homeButton = addButton(R.drawable.ic_sysbar_home, BUTTON_HOME, navContainer,
                navButtonController, R.id.home);
        mPropertyHolders.add(new StatePropertyHolder(homeButton,
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0 &&
                        (flags & FLAG_DISABLE_HOME) == 0));
        View recentsButton = addButton(R.drawable.ic_sysbar_recent, BUTTON_RECENTS,
                navContainer, navButtonController, R.id.recent_apps);
        mPropertyHolders.add(new StatePropertyHolder(recentsButton,
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0 &&
                        (flags & FLAG_DISABLE_RECENTS) == 0));

        // A11y button
        mA11yButton = addButton(R.drawable.ic_sysbar_accessibility_button, BUTTON_A11Y,
                endContainer, navButtonController, R.id.accessibility_button,
                R.layout.taskbar_contextual_button);
        mPropertyHolders.add(new StatePropertyHolder(mA11yButton,
                flags -> (flags & FLAG_A11Y_VISIBLE) != 0
                        && (flags & FLAG_ROTATION_BUTTON_VISIBLE) == 0));
    }

    private void parseSystemUiFlags(int sysUiStateFlags) {
        mSysuiStateFlags = sysUiStateFlags;
        boolean isImeVisible = (sysUiStateFlags & SYSUI_STATE_IME_SHOWING) != 0;
        boolean isImeSwitcherShowing = (sysUiStateFlags & SYSUI_STATE_IME_SWITCHER_SHOWING) != 0;
        boolean a11yVisible = (sysUiStateFlags & SYSUI_STATE_A11Y_BUTTON_CLICKABLE) != 0;
        boolean isHomeDisabled = (sysUiStateFlags & SYSUI_STATE_HOME_DISABLED) != 0;
        boolean isRecentsDisabled = (sysUiStateFlags & SYSUI_STATE_OVERVIEW_DISABLED) != 0;
        boolean isBackDisabled = (sysUiStateFlags & SYSUI_STATE_BACK_DISABLED) != 0;
        int shadeExpandedFlags = SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
                | SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
        boolean isNotificationShadeExpanded = (sysUiStateFlags & shadeExpandedFlags) != 0;

        // TODO(b/202218289) we're getting IME as not visible on lockscreen from system
        updateStateForFlag(FLAG_IME_VISIBLE, isImeVisible);
        updateStateForFlag(FLAG_SWITCHER_SUPPORTED, isImeSwitcherShowing);
        updateStateForFlag(FLAG_A11Y_VISIBLE, a11yVisible);
        updateStateForFlag(FLAG_DISABLE_HOME, isHomeDisabled);
        updateStateForFlag(FLAG_DISABLE_RECENTS, isRecentsDisabled);
        updateStateForFlag(FLAG_DISABLE_BACK, isBackDisabled);
        updateStateForFlag(FLAG_NOTIFICATION_SHADE_EXPANDED, isNotificationShadeExpanded);

        if (mA11yButton != null) {
            // Only used in 3 button
            boolean a11yLongClickable =
                    (sysUiStateFlags & SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE) != 0;
            mA11yButton.setLongClickable(a11yLongClickable);
        }
    }

    public void updateStateForSysuiFlags(int systemUiStateFlags, boolean skipAnim) {
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

    /**
     * Returns true if IME bar is visible
     */
    public boolean isImeVisible() {
        return (mState & FLAG_IME_VISIBLE) != 0;
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
        return (mState & FLAG_DISABLE_RECENTS) != 0;
    }

    /**
     * Adds the bounds corresponding to all visible buttons to provided region
     */
    public void addVisibleButtonsRegion(TaskbarDragLayer parent, Region outRegion) {
        int count = mAllButtons.size();
        for (int i = 0; i < count; i++) {
            View button = mAllButtons.get(i);
            if (button.getVisibility() == View.VISIBLE) {
                parent.getDescendantRectRelativeToSelf(button, mTempRect);
                outRegion.op(mTempRect, Op.UNION);
            }
        }
    }

    /** Use to set the translationY for the all nav+contextual buttons */
    public AnimatedFloat getTaskbarNavButtonTranslationY() {
        return mTaskbarNavButtonTranslationY;
    }

    /** Use to set the dark intensity for the all nav+contextual buttons */
    public AnimatedFloat getTaskbarNavButtonDarkIntensity() {
        return mTaskbarNavButtonDarkIntensity;
    }

    /** Use to determine whether to use the dark intensity requested by the underlying app */
    public AnimatedFloat getNavButtonDarkIntensityMultiplier() {
        return mNavButtonDarkIntensityMultiplier;
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
            mPropertyHolders.get(i).setState(mState);
        }
    }

    private void updateNavButtonTranslationY() {
        mNavButtonsView.setTranslationY(mTaskbarNavButtonTranslationY.value
                * mNavButtonTranslationYMultiplier.value);
    }

    private void updateNavButtonDarkIntensity() {
        float darkIntensity = mTaskbarNavButtonDarkIntensity.value
                * mNavButtonDarkIntensityMultiplier.value;
        int iconColor = (int) ArgbEvaluator.getInstance().evaluate(darkIntensity, mLightIconColor,
                mDarkIconColor);
        for (ImageView button : mAllButtons) {
            button.setImageTintList(ColorStateList.valueOf(iconColor));
        }
    }

    private ImageView addButton(@DrawableRes int drawableId, @TaskbarButton int buttonType,
            ViewGroup parent, TaskbarNavButtonController navButtonController, @IdRes int id) {
        return addButton(drawableId, buttonType, parent, navButtonController, id,
                R.layout.taskbar_nav_button);
    }

    private ImageView addButton(@DrawableRes int drawableId, @TaskbarButton int buttonType,
            ViewGroup parent, TaskbarNavButtonController navButtonController, @IdRes int id,
            @LayoutRes int layoutId) {
        ImageView buttonView = addButton(parent, id, layoutId);
        buttonView.setImageResource(drawableId);
        buttonView.setOnClickListener(view -> navButtonController.onButtonClick(buttonType));
        buttonView.setOnLongClickListener(view ->
                navButtonController.onButtonLongClick(buttonType));
        return buttonView;
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

    public void onDestroy() {
        mPropertyHolders.clear();
        mControllers.rotationButtonController.unregisterListeners();
        if (mFloatingRotationButton != null) {
            mFloatingRotationButton.hide();
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

    private class RotationButtonImpl implements RotationButton {

        private final ImageView mButton;
        private AnimatedVectorDrawable mImageDrawable;

        RotationButtonImpl(ImageView button) {
            mButton = button;
        }

        @Override
        public void setRotationButtonController(RotationButtonController rotationButtonController) {
            // TODO(b/187754252) UI polish, different icons based on light/dark context, etc
            mImageDrawable = (AnimatedVectorDrawable) mButton.getContext()
                    .getDrawable(rotationButtonController.getIconResId());
            mButton.setImageDrawable(mImageDrawable);
            mButton.setContentDescription(mButton.getResources()
                    .getString(R.string.accessibility_rotate_button));
            mImageDrawable.setCallback(mButton);
        }

        @Override
        public View getCurrentView() {
            return mButton;
        }

        @Override
        public boolean show() {
            mButton.setVisibility(View.VISIBLE);
            mState |= FLAG_ROTATION_BUTTON_VISIBLE;
            applyState();
            return true;
        }

        @Override
        public boolean hide() {
            mButton.setVisibility(View.GONE);
            mState &= ~FLAG_ROTATION_BUTTON_VISIBLE;
            applyState();
            return true;
        }

        @Override
        public boolean isVisible() {
            return mButton.getVisibility() == View.VISIBLE;
        }

        @Override
        public void updateIcon(int lightIconColor, int darkIconColor) {
            // TODO(b/187754252): UI Polish
        }

        @Override
        public void setOnClickListener(OnClickListener onClickListener) {
            mButton.setOnClickListener(onClickListener);
        }

        @Override
        public void setOnHoverListener(OnHoverListener onHoverListener) {
            mButton.setOnHoverListener(onHoverListener);
        }

        @Override
        public AnimatedVectorDrawable getImageDrawable() {
            return mImageDrawable;
        }

        @Override
        public void setDarkIntensity(float darkIntensity) {
            // TODO(b/187754252) UI polish
        }

        @Override
        public boolean acceptRotationProposal() {
            return mButton.isAttachedToWindow();
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

        <T> StatePropertyHolder(T target, IntPredicate enabledCondition,
                Property<T, Float> property, float enabledValue, float disabledValue) {
            mEnableCondition = enabledCondition;
            mEnabledValue = enabledValue;
            mDisabledValue = disabledValue;
            mAnimator = ObjectAnimator.ofFloat(target, property, enabledValue, disabledValue);
        }

        public void setState(int flags) {
            boolean isEnabled = mEnableCondition.test(flags);
            if (mIsEnabled != isEnabled) {
                mIsEnabled = isEnabled;
                mAnimator.cancel();
                mAnimator.setFloatValues(mIsEnabled ? mEnabledValue : mDisabledValue);
                mAnimator.start();
            }
        }

        public void endAnimation() {
            if (mAnimator.isRunning()) {
                mAnimator.end();
            }
        }
    }
}
