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

import static com.android.app.animation.Interpolators.FINAL_FRAME;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.Utilities.mapRange;
import static com.android.launcher3.anim.AnimatedFloat.VALUE;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarPinning;
import static com.android.launcher3.taskbar.TaskbarPinningController.PINNING_PERSISTENT;
import static com.android.launcher3.taskbar.TaskbarPinningController.PINNING_TRANSIENT;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_ALIGNMENT_ANIM;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_PINNING_ANIM;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_REVEAL_ANIM;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.core.view.OneShotPreDrawListener;

import com.android.app.animation.Interpolators;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.RevealOutlineAnimation;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.views.IconButtonView;

import java.io.PrintWriter;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Handles properties/data collection, then passes the results to TaskbarView to render.
 */
public class TaskbarViewController implements TaskbarControllers.LoggableTaskbarController {

    private static final String TAG = "TaskbarViewController";

    private static final Runnable NO_OP = () -> { };

    public static final int ALPHA_INDEX_HOME = 0;
    public static final int ALPHA_INDEX_KEYGUARD = 1;
    public static final int ALPHA_INDEX_STASH = 2;
    public static final int ALPHA_INDEX_RECENTS_DISABLED = 3;
    public static final int ALPHA_INDEX_NOTIFICATION_EXPANDED = 4;
    public static final int ALPHA_INDEX_ASSISTANT_INVOKED = 5;
    public static final int ALPHA_INDEX_SMALL_SCREEN = 6;
    private static final int NUM_ALPHA_CHANNELS = 7;

    private final TaskbarActivityContext mActivity;
    private final TaskbarView mTaskbarView;
    private final MultiValueAlpha mTaskbarIconAlpha;
    private final AnimatedFloat mTaskbarIconScaleForStash = new AnimatedFloat(this::updateScale);
    private final AnimatedFloat mTaskbarIconTranslationYForHome = new AnimatedFloat(
            this::updateTranslationY);
    private final AnimatedFloat mTaskbarIconTranslationYForStash = new AnimatedFloat(
            this::updateTranslationY);

    private final AnimatedFloat mTaskbarIconScaleForPinning = new AnimatedFloat(
            this::updateTaskbarIconsScale);

    private final AnimatedFloat mTaskbarIconTranslationXForPinning = new AnimatedFloat(
            this::updateTaskbarIconTranslationXForPinning);

    private final AnimatedFloat mTaskbarIconTranslationYForPinning = new AnimatedFloat(
            this::updateTranslationY);

    private final View.OnLayoutChangeListener mTaskbarViewLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom)
                    -> updateTaskbarIconTranslationXForPinning();


    private AnimatedFloat mTaskbarNavButtonTranslationY;
    private AnimatedFloat mTaskbarNavButtonTranslationYForInAppDisplay;
    private float mTaskbarIconTranslationYForSwipe;
    private float mTaskbarIconTranslationYForSpringOnStash;

    private int mTaskbarBottomMargin;
    private final int mStashedHandleHeight;

    private final TaskbarModelCallbacks mModelCallbacks;

    // Initialized in init.
    private TaskbarControllers mControllers;

    // Animation to align icons with Launcher, created lazily. This allows the controller to be
    // active only during the animation and does not need to worry about layout changes.
    private AnimatorPlaybackController mIconAlignControllerLazy = null;
    private Runnable mOnControllerPreCreateCallback = NO_OP;

    // Stored here as signals to determine if the mIconAlignController needs to be recreated.
    private boolean mIsHotseatIconOnTopWhenAligned;
    private boolean mIsStashed;

    private final DeviceProfile.OnDeviceProfileChangeListener mDeviceProfileChangeListener =
            dp -> commitRunningAppsToUI();

    private final boolean mIsRtl;

    private final DeviceProfile mTransientTaskbarDp;
    private final DeviceProfile mPersistentTaskbarDp;

    private final int mTransientIconSize;
    private final int mPersistentIconSize;

    private final float mTaskbarLeftRightMargin;

    public TaskbarViewController(TaskbarActivityContext activity, TaskbarView taskbarView) {
        mActivity = activity;
        mTransientTaskbarDp = mActivity.getTransientTaskbarDeviceProfile();
        mPersistentTaskbarDp = mActivity.getPersistentTaskbarDeviceProfile();
        mTransientIconSize = mTransientTaskbarDp.taskbarIconSize;
        mPersistentIconSize = mPersistentTaskbarDp.taskbarIconSize;
        mTaskbarView = taskbarView;
        mTaskbarIconAlpha = new MultiValueAlpha(mTaskbarView, NUM_ALPHA_CHANNELS);
        mTaskbarIconAlpha.setUpdateVisibility(true);
        mModelCallbacks = TaskbarModelCallbacksFactory.newInstance(mActivity)
                .create(mActivity, mTaskbarView);
        mTaskbarBottomMargin = activity.getDeviceProfile().taskbarBottomMargin;
        mStashedHandleHeight = activity.getResources()
                .getDimensionPixelSize(R.dimen.taskbar_stashed_handle_height);

        mIsRtl = Utilities.isRtl(mTaskbarView.getResources());
        mTaskbarLeftRightMargin = mActivity.getResources().getDimensionPixelSize(
                R.dimen.transient_taskbar_padding);

    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        mTaskbarView.init(TaskbarViewCallbacksFactory.newInstance(mActivity).create(
                mActivity, mControllers, mTaskbarView));
        mTaskbarView.getLayoutParams().height = mActivity.isPhoneMode()
                ? mActivity.getResources().getDimensionPixelSize(R.dimen.taskbar_phone_size)
                : mActivity.getDeviceProfile().taskbarHeight;

        mTaskbarIconScaleForStash.updateValue(1f);
        float pinningValue = DisplayController.isTransientTaskbar(mActivity)
                ? PINNING_TRANSIENT
                : PINNING_PERSISTENT;
        mTaskbarIconScaleForPinning.updateValue(pinningValue);
        mTaskbarIconTranslationYForPinning.updateValue(pinningValue);
        mTaskbarIconTranslationXForPinning.updateValue(pinningValue);

        mModelCallbacks.init(controllers);
        if (mActivity.isUserSetupComplete()) {
            // Only load the callbacks if user setup is completed
            LauncherAppState.getInstance(mActivity).getModel().addCallbacksAndLoad(mModelCallbacks);
        }
        mTaskbarNavButtonTranslationY =
                controllers.navbarButtonsViewController.getTaskbarNavButtonTranslationY();
        mTaskbarNavButtonTranslationYForInAppDisplay = controllers.navbarButtonsViewController
                .getTaskbarNavButtonTranslationYForInAppDisplay();

        mActivity.addOnDeviceProfileChangeListener(mDeviceProfileChangeListener);

        if (ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
            // This gets modified in NavbarButtonsViewController, but the initial value it reads
            // may be incorrect since it's state gets destroyed on taskbar recreate, so reset here
            mTaskbarIconAlpha.get(ALPHA_INDEX_SMALL_SCREEN)
                    .animateToValue(mActivity.isPhoneButtonNavMode() ? 0 : 1).start();
        }
        if (enableTaskbarPinning()) {
            mTaskbarView.addOnLayoutChangeListener(mTaskbarViewLayoutChangeListener);
        }
    }

    /**
     * Announcement for Accessibility when Taskbar stashes/unstashes.
     */
    public void announceForAccessibility() {
        mTaskbarView.announceAccessibilityChanges();
    }

    public void onDestroy() {
        if (enableTaskbarPinning()) {
            mTaskbarView.removeOnLayoutChangeListener(mTaskbarViewLayoutChangeListener);
        }
        LauncherAppState.getInstance(mActivity).getModel().removeCallbacks(mModelCallbacks);
        mActivity.removeOnDeviceProfileChangeListener(mDeviceProfileChangeListener);
        mModelCallbacks.unregisterListeners();
    }

    public boolean areIconsVisible() {
        return mTaskbarView.areIconsVisible();
    }

    public MultiPropertyFactory<View> getTaskbarIconAlpha() {
        return mTaskbarIconAlpha;
    }

    /**
     * Should be called when the recents button is disabled, so we can hide Taskbar icons as well.
     */
    public void setRecentsButtonDisabled(boolean isDisabled) {
        // TODO: check TaskbarStashController#supportsStashing(), to stash instead of setting alpha.
        mTaskbarIconAlpha.get(ALPHA_INDEX_RECENTS_DISABLED).setValue(isDisabled ? 0 : 1);
    }

    /**
     * Sets OnClickListener and OnLongClickListener for the given view.
     */
    public void setClickAndLongClickListenersForIcon(View icon) {
        mTaskbarView.setClickAndLongClickListenersForIcon(icon);
    }

    /**
     * Adds one time pre draw listener to the Taskbar view, it is called before
     * drawing a frame and invoked only once
     * @param listener callback that will be invoked before drawing the next frame
     */
    public void addOneTimePreDrawListener(@NonNull Runnable listener) {
        OneShotPreDrawListener.add(mTaskbarView, listener);
    }

    public Rect getIconLayoutBounds() {
        return mTaskbarView.getIconLayoutBounds();
    }

    public int getIconLayoutWidth() {
        return mTaskbarView.getIconLayoutWidth();
    }

    public View[] getIconViews() {
        return mTaskbarView.getIconViews();
    }

    @Nullable
    public View getAllAppsButtonView() {
        return mTaskbarView.getAllAppsButtonView();
    }

    public AnimatedFloat getTaskbarIconScaleForStash() {
        return mTaskbarIconScaleForStash;
    }

    public AnimatedFloat getTaskbarIconTranslationYForStash() {
        return mTaskbarIconTranslationYForStash;
    }

    public AnimatedFloat getTaskbarIconScaleForPinning() {
        return mTaskbarIconScaleForPinning;
    }

    public AnimatedFloat getTaskbarIconTranslationXForPinning() {
        return mTaskbarIconTranslationXForPinning;
    }

    public AnimatedFloat getTaskbarIconTranslationYForPinning() {
        return mTaskbarIconTranslationYForPinning;
    }

    /**
     * Applies scale properties for the entire TaskbarView (rather than individual icons).
     */
    private void updateScale() {
        float scale = mTaskbarIconScaleForStash.value;
        mTaskbarView.setScaleX(scale);
        mTaskbarView.setScaleY(scale);
    }

    /**
     * Applies scale properties for the taskbar icons
     */
    private void updateTaskbarIconsScale() {
        float scale = mTaskbarIconScaleForPinning.value;
        View[] iconViews = mTaskbarView.getIconViews();

        float finalScale;
        if (mControllers.getSharedState().startTaskbarVariantIsTransient) {
            finalScale = mapRange(scale, 1f, ((float) mPersistentIconSize / mTransientIconSize));
        } else {
            finalScale = mapRange(scale, ((float) mTransientIconSize / mPersistentIconSize), 1f);
        }

        for (int iconIndex = 0; iconIndex < iconViews.length; iconIndex++) {
            iconViews[iconIndex].setScaleX(finalScale);
            iconViews[iconIndex].setScaleY(finalScale);
        }
    }

    /**
     * Animate away taskbar icon notification dots during the taskbar pinning animation.
     */
    public void animateAwayNotificationDotsDuringTaskbarPinningAnimation() {
        for (View iconView : mTaskbarView.getIconViews()) {
            if (iconView instanceof BubbleTextView && ((BubbleTextView) iconView).hasDot()) {
                ((BubbleTextView) iconView).animateDotScale(0);
            }
        }
    }

    private void updateTaskbarIconTranslationXForPinning() {
        View[] iconViews = mTaskbarView.getIconViews();
        float scale = mTaskbarIconTranslationXForPinning.value;
        float transientTaskbarAllAppsOffset = mActivity.getResources().getDimension(
                mTaskbarView.getAllAppsButtonTranslationXOffset(true));
        float persistentTaskbarAllAppsOffset = mActivity.getResources().getDimension(
                mTaskbarView.getAllAppsButtonTranslationXOffset(false));

        float allAppIconTranslateRange = mapRange(scale, transientTaskbarAllAppsOffset,
                persistentTaskbarAllAppsOffset);

        if (mIsRtl) {
            allAppIconTranslateRange *= -1;
        }

        if (mActivity.isThreeButtonNav()) {
            ((IconButtonView) mTaskbarView.getAllAppsButtonView())
                    .setTranslationXForTaskbarAllAppsIcon(allAppIconTranslateRange);
            return;
        }

        float taskbarCenterX =
                mTaskbarView.getLeft() + (mTaskbarView.getRight() - mTaskbarView.getLeft()) / 2.0f;

        float finalMarginScale = mapRange(scale, 0f, mTransientIconSize - mPersistentIconSize);

        float halfIconCount = iconViews.length / 2.0f;
        for (int iconIndex = 0; iconIndex < iconViews.length; iconIndex++) {
            View iconView = iconViews[iconIndex];
            MultiTranslateDelegate translateDelegate =
                    ((Reorderable) iconView).getTranslateDelegate();
            float iconCenterX =
                    iconView.getLeft() + (iconView.getRight() - iconView.getLeft()) / 2.0f;
            if (iconCenterX <= taskbarCenterX) {
                translateDelegate.getTranslationX(INDEX_TASKBAR_PINNING_ANIM).setValue(
                        finalMarginScale * (halfIconCount - iconIndex));
            } else {
                translateDelegate.getTranslationX(INDEX_TASKBAR_PINNING_ANIM).setValue(
                        -finalMarginScale * (iconIndex - halfIconCount));
            }

            if (iconView.equals(mTaskbarView.getAllAppsButtonView()) && iconViews.length > 1) {
                ((IconButtonView) iconView).setTranslationXForTaskbarAllAppsIcon(
                        allAppIconTranslateRange);
            }
        }
    }

    /**
     * Calculates visual taskbar view width.
     */
    public float getCurrentVisualTaskbarWidth() {
        if (mTaskbarView.getIconViews().length == 0) {
            return 0;
        }

        View[] iconViews = mTaskbarView.getIconViews();

        int leftIndex = mActivity.getDeviceProfile().isQsbInline && !mIsRtl ? 1 : 0;
        int rightIndex = mActivity.getDeviceProfile().isQsbInline && mIsRtl
                ? iconViews.length - 2
                : iconViews.length - 1;

        float left = iconViews[leftIndex].getX();
        float right = iconViews[rightIndex].getRight() + iconViews[rightIndex].getTranslationX();

        return right - left + (2 * mTaskbarLeftRightMargin);
    }

    /**
     * Sets the translation of the TaskbarView during the swipe up gesture.
     */
    public void setTranslationYForSwipe(float transY) {
        mTaskbarIconTranslationYForSwipe = transY;
        updateTranslationY();
    }

    /**
     * Sets the translation of the TaskbarView during the spring on stash animation.
     */
    public void setTranslationYForStash(float transY) {
        mTaskbarIconTranslationYForSpringOnStash = transY;
        updateTranslationY();
    }

    private void updateTranslationY() {
        mTaskbarView.setTranslationY(mTaskbarIconTranslationYForHome.value
                + mTaskbarIconTranslationYForStash.value
                + mTaskbarIconTranslationYForSwipe
                + getTaskbarIconTranslationYForPinningValue()
                + mTaskbarIconTranslationYForSpringOnStash);
    }

    /**
     * Computes translation y for taskbar pinning.
     */
    private float getTaskbarIconTranslationYForPinningValue() {
        if (mControllers.getSharedState() == null) return 0f;

        float scale = mTaskbarIconTranslationYForPinning.value;
        float taskbarIconTranslationYForPinningValue;

        // transY is calculated here by adding/subtracting the taskbar bottom margin
        // aligning the icon bound to be at bottom of current taskbar view and then
        // finally placing the icon in the middle of new taskbar background height.
        if (mControllers.getSharedState().startTaskbarVariantIsTransient) {
            float transY =
                    mTransientTaskbarDp.taskbarBottomMargin + (mTransientTaskbarDp.taskbarHeight
                            - mTaskbarView.getIconLayoutBounds().bottom)
                            - (mPersistentTaskbarDp.taskbarHeight
                                    - mTransientTaskbarDp.taskbarIconSize) / 2f;
            taskbarIconTranslationYForPinningValue = mapRange(scale, 0f, transY);
        } else {
            float transY =
                    -mTransientTaskbarDp.taskbarBottomMargin + (mPersistentTaskbarDp.taskbarHeight
                            - mTaskbarView.getIconLayoutBounds().bottom)
                            - (mTransientTaskbarDp.taskbarHeight
                                    - mTransientTaskbarDp.taskbarIconSize) / 2f;
            taskbarIconTranslationYForPinningValue = mapRange(scale, transY, 0f);
        }
        return taskbarIconTranslationYForPinningValue;
    }

    private ValueAnimator createRevealAnimForView(View view, boolean isStashed, float newWidth,
            boolean isQsb, boolean dispatchOnAnimationStart) {
        Rect viewBounds = new Rect(0, 0, view.getWidth(), view.getHeight());
        int centerY = viewBounds.centerY();
        int halfHandleHeight = mStashedHandleHeight / 2;
        final int top = centerY - halfHandleHeight;
        final int bottom = centerY + halfHandleHeight;

        final int left;
        final int right;
        // QSB will crop from the 'start' whereas all other icons will crop from the center.
        if (isQsb) {
            if (mIsRtl) {
                right = viewBounds.right;
                left = (int) (right - newWidth);
            } else {
                left = viewBounds.left;
                right = (int) (left + newWidth);
            }
        } else {
            int widthDelta = (int) ((viewBounds.width() - newWidth) / 2);

            left = viewBounds.left + widthDelta;
            right = viewBounds.right - widthDelta;
        }

        Rect stashedRect = new Rect(left, top, right, bottom);
        // QSB radius can be > 0 since it does not have any UI elements outside of it bounds.
        float radius = isQsb
                ? viewBounds.height() / 2f
                : 0f;
        float stashedRadius = stashedRect.height() / 2f;

        ValueAnimator reveal = new RoundedRectRevealOutlineProvider(radius,
                stashedRadius, viewBounds, stashedRect)
                .createRevealAnimator(view, !isStashed, 0);
        // SUW animation does not dispatch animation start until *after* the animation is complete.
        // In order to work properly, the reveal animation start needs to be called immediately.
        if (dispatchOnAnimationStart) {
            for (Animator.AnimatorListener listener : reveal.getListeners()) {
                listener.onAnimationStart(reveal);
            }
        }
        return reveal;
    }

    public View getTaskbarDividerView() {
        return mTaskbarView.getTaskbarDividerView();
    }

    /** Updates which icons are marked as running given the Set of currently running packages. */
    public void updateIconViewsRunningStates(Set<String> runningPackages) {
        for (View iconView : getIconViews()) {
            if (iconView instanceof BubbleTextView btv) {
                btv.updateRunningState(runningPackages.contains(btv.getTargetPackageName()));
            }
        }
    }

    /**
     * Defers any updates to the UI for the setup wizard animation.
     */
    public void setDeferUpdatesForSUW(boolean defer) {
        mModelCallbacks.setDeferUpdatesForSUW(defer);
    }

    /**
     * Creates and returns a {@link RevealOutlineAnimation} Animator that updates the icon shape
     * and size.
     * @param as The AnimatorSet to add all animations to.
     * @param isStashed When true, the icon crops vertically to the size of the stashed handle.
     *                  When false, the reverse happens.
     * @param duration The duration of the animation.
     * @param interpolator The interpolator to use for all animations.
     */
    public void addRevealAnimToIsStashed(AnimatorSet as, boolean isStashed, long duration,
            Interpolator interpolator, boolean dispatchOnAnimationStart) {
        AnimatorSet reveal = new AnimatorSet();

        Rect stashedBounds = new Rect();
        mControllers.stashedHandleViewController.getStashedHandleBounds(stashedBounds);

        int numIcons = mTaskbarView.getChildCount();
        float newChildWidth = stashedBounds.width() / (float) numIcons;

        // All children move the same y-amount since they will be cropped to the same centerY.
        float croppedTransY = mTaskbarView.getIconTouchSize() - stashedBounds.height();

        for (int i = mTaskbarView.getChildCount() - 1; i >= 0; i--) {
            View child = mTaskbarView.getChildAt(i);
            boolean isQsb = child == mTaskbarView.getQsb();

            // Crop the icons to/from the nav handle shape.
            reveal.play(createRevealAnimForView(child, isStashed, newChildWidth, isQsb,
                    dispatchOnAnimationStart).setDuration(duration));

            // Translate the icons to/from their locations as the "nav handle."

            // All of the Taskbar icons will overlap the entirety of the stashed handle
            // And the QSB, if inline, will overlap part of stashed handle as well.
            float currentPosition = isQsb ? child.getX() : child.getLeft();
            float newPosition = stashedBounds.left + (newChildWidth * i);
            final float croppedTransX;
            // We look at 'left' and 'right' values to ensure that the children stay within the
            // bounds of the stashed handle since the new width only occurs at the end of the anim.
            if (currentPosition > newPosition) {
                float newRight = stashedBounds.right - (newChildWidth
                        * (numIcons - 1 - i));
                croppedTransX = -(currentPosition + child.getWidth() - newRight);
            } else {
                croppedTransX = newPosition - currentPosition;
            }
            float[] transX = isStashed
                    ? new float[] {croppedTransX}
                    : new float[] {croppedTransX, 0};
            float[] transY = isStashed
                    ? new float[] {croppedTransY}
                    : new float[] {croppedTransY, 0};

            if (child instanceof Reorderable) {
                MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();

                reveal.play(ObjectAnimator.ofFloat(mtd.getTranslationX(INDEX_TASKBAR_REVEAL_ANIM),
                        MULTI_PROPERTY_VALUE, transX)
                        .setDuration(duration));
                reveal.play(ObjectAnimator.ofFloat(mtd.getTranslationY(INDEX_TASKBAR_REVEAL_ANIM),
                        MULTI_PROPERTY_VALUE, transY));
                as.addListener(forEndCallback(() ->
                        mtd.setTranslation(INDEX_TASKBAR_REVEAL_ANIM, 0, 0)));
            } else {
                reveal.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_X, transX)
                        .setDuration(duration));
                reveal.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_Y, transY));
                as.addListener(forEndCallback(() -> {
                    child.setTranslationX(0);
                    child.setTranslationY(0);
                }));
            }
        }

        reveal.setInterpolator(interpolator);
        as.play(reveal);
    }

    /**
     * Sets the Taskbar icon alignment relative to Launcher hotseat icons
     * @param alignmentRatio [0, 1]
     *                       0 => not aligned
     *                       1 => fully aligned
     */
    public void setLauncherIconAlignment(float alignmentRatio, DeviceProfile launcherDp) {
        if (mActivity.isPhoneMode()) {
            mIconAlignControllerLazy = null;
            return;
        }

        boolean isHotseatIconOnTopWhenAligned =
                mControllers.uiController.isHotseatIconOnTopWhenAligned();
        boolean isStashed = mControllers.taskbarStashController.isStashed();
        // Re-create animation when mIsHotseatIconOnTopWhenAligned or mIsStashed changes.
        if (mIconAlignControllerLazy == null
                || mIsHotseatIconOnTopWhenAligned != isHotseatIconOnTopWhenAligned
                || mIsStashed != isStashed) {
            mIsHotseatIconOnTopWhenAligned = isHotseatIconOnTopWhenAligned;
            mIsStashed = isStashed;
            mIconAlignControllerLazy = createIconAlignmentController(launcherDp);
        }
        mIconAlignControllerLazy.setPlayFraction(alignmentRatio);
        if (alignmentRatio <= 0 || alignmentRatio >= 1) {
            // Cleanup lazy controller so that it is created again in next animation
            mIconAlignControllerLazy = null;
        }
    }

    /** Resets the icon alignment controller so that it can be recreated again later. */
    void resetIconAlignmentController() {
        mIconAlignControllerLazy = null;
    }

    /**
     * Creates an animation for aligning the Taskbar icons with the provided Launcher device profile
     */
    private AnimatorPlaybackController createIconAlignmentController(DeviceProfile launcherDp) {
        PendingAnimation setter = new PendingAnimation(100);
        mOnControllerPreCreateCallback.run();
        DeviceProfile taskbarDp = mActivity.getDeviceProfile();
        Rect hotseatPadding = launcherDp.getHotseatLayoutPadding(mActivity);
        boolean isTransientTaskbar = DisplayController.isTransientTaskbar(mActivity);

        float scaleUp = ((float) launcherDp.iconSizePx) / taskbarDp.taskbarIconSize;
        int borderSpacing = launcherDp.hotseatBorderSpace;
        int hotseatCellSize = DeviceProfile.calculateCellWidth(
                launcherDp.availableWidthPx - hotseatPadding.left - hotseatPadding.right,
                borderSpacing,
                launcherDp.numShownHotseatIcons);

        boolean isToHome = mControllers.uiController.isIconAlignedWithHotseat();
        // If Hotseat is not the top element, Taskbar should maintain in-app state as it fades out,
        // or fade in while already in in-app state.
        Interpolator interpolator = mIsHotseatIconOnTopWhenAligned ? LINEAR : FINAL_FRAME;

        int offsetY = launcherDp.getTaskbarOffsetY();
        setter.setFloat(mTaskbarIconTranslationYForHome, VALUE, -offsetY, interpolator);
        setter.setFloat(mTaskbarNavButtonTranslationY, VALUE, -offsetY, interpolator);
        setter.setFloat(mTaskbarNavButtonTranslationYForInAppDisplay, VALUE, offsetY, interpolator);

        int collapsedHeight = mActivity.getDefaultTaskbarWindowSize();
        int expandedHeight = Math.max(collapsedHeight, taskbarDp.taskbarHeight + offsetY);
        setter.addOnFrameListener(anim -> mActivity.setTaskbarWindowSize(
                anim.getAnimatedFraction() > 0 ? expandedHeight : collapsedHeight));

        mTaskbarBottomMargin = isTransientTaskbar
                ? mTransientTaskbarDp.taskbarBottomMargin
                : mPersistentTaskbarDp.taskbarBottomMargin;

        for (int i = 0; i < mTaskbarView.getChildCount(); i++) {
            View child = mTaskbarView.getChildAt(i);
            boolean isAllAppsButton = child == mTaskbarView.getAllAppsButtonView();
            boolean isTaskbarDividerView = child == mTaskbarView.getTaskbarDividerView();
            if (!mIsHotseatIconOnTopWhenAligned) {
                // When going to home, the EMPHASIZED interpolator in TaskbarLauncherStateController
                // plays iconAlignment to 1 really fast, therefore moving the fading towards the end
                // to avoid icons disappearing rather than fading out visually.
                setter.setViewAlpha(child, 0, Interpolators.clampToProgress(LINEAR, 0.8f, 1f));
            } else if ((isAllAppsButton && !FeatureFlags.ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT.get())
                    || (isTaskbarDividerView && enableTaskbarPinning())) {
                if (!isToHome
                        && mIsHotseatIconOnTopWhenAligned
                        && mIsStashed) {
                    // Prevent All Apps icon from appearing when going from hotseat to nav handle.
                    setter.setViewAlpha(child, 0, Interpolators.clampToProgress(LINEAR, 0f, 0f));
                } else if (enableScalingRevealHomeAnimation()) {
                    // Tighten clamp so that these icons do not linger as the spring settles.
                    setter.setViewAlpha(child, 0,
                            isToHome
                                    ? Interpolators.clampToProgress(LINEAR, 0f, 0.07f)
                                    : Interpolators.clampToProgress(LINEAR, 0.93f, 1f));
                } else {
                    setter.setViewAlpha(child, 0,
                            isToHome
                                    ? Interpolators.clampToProgress(LINEAR, 0f, 0.17f)
                                    : Interpolators.clampToProgress(LINEAR, 0.72f, 0.84f));
                }
            }

            if (child == mTaskbarView.getQsb()) {
                boolean isRtl = Utilities.isRtl(child.getResources());
                float hotseatIconCenter = isRtl
                        ? launcherDp.widthPx - hotseatPadding.right + borderSpacing
                        + launcherDp.hotseatQsbWidth / 2f
                        : hotseatPadding.left - borderSpacing - launcherDp.hotseatQsbWidth / 2f;
                float childCenter = (child.getLeft() + child.getRight()) / 2f;
                childCenter += ((Reorderable) child).getTranslateDelegate().getTranslationX(
                        INDEX_TASKBAR_PINNING_ANIM).getValue();
                float halfQsbIconWidthDiff =
                        (launcherDp.hotseatQsbWidth - taskbarDp.taskbarIconSize) / 2f;
                float scale = ((float) taskbarDp.taskbarIconSize)
                        / launcherDp.hotseatQsbVisualHeight;
                setter.addFloat(child, SCALE_PROPERTY, scale, 1f, interpolator);

                float fromX = isRtl ? -halfQsbIconWidthDiff : halfQsbIconWidthDiff;
                float toX = hotseatIconCenter - childCenter;
                if (child instanceof Reorderable) {
                    MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();

                    setter.addFloat(mtd.getTranslationX(INDEX_TASKBAR_ALIGNMENT_ANIM),
                            MULTI_PROPERTY_VALUE, fromX, toX, interpolator);
                    setter.setFloat(mtd.getTranslationY(INDEX_TASKBAR_ALIGNMENT_ANIM),
                            MULTI_PROPERTY_VALUE, mTaskbarBottomMargin, interpolator);
                } else {
                    setter.addFloat(child, VIEW_TRANSLATE_X, fromX, toX, interpolator);
                    setter.setFloat(child, VIEW_TRANSLATE_Y, mTaskbarBottomMargin, interpolator);
                }

                if (mIsHotseatIconOnTopWhenAligned) {
                    setter.addFloat(child, VIEW_ALPHA, 0f, 1f,
                            isToHome
                                    ? Interpolators.clampToProgress(LINEAR, 0f, 0.35f)
                                    : mActivity.getDeviceProfile().isQsbInline
                                            ? Interpolators.clampToProgress(LINEAR, 0f, 1f)
                                            : Interpolators.clampToProgress(LINEAR, 0.84f, 1f));
                }
                setter.addOnFrameListener(animator -> AlphaUpdateListener.updateVisibility(child));
                continue;
            }

            float positionInHotseat;
            if (isAllAppsButton) {
                // Note that there is no All Apps button in the hotseat,
                // this position is only used as its convenient for animation purposes.
                positionInHotseat = Utilities.isRtl(child.getResources())
                        ? taskbarDp.numShownHotseatIcons
                        : -1;
            }  else if (isTaskbarDividerView) {
                // Note that there is no taskbar divider view in the hotseat,
                // this position is only used as its convenient for animation purposes.
                positionInHotseat = Utilities.isRtl(child.getResources())
                        ? taskbarDp.numShownHotseatIcons - 0.5f
                        : -0.5f;
            } else if (child.getTag() instanceof ItemInfo) {
                positionInHotseat = ((ItemInfo) child.getTag()).screenId;
            } else {
                Log.w(TAG, "Unsupported view found in createIconAlignmentController, v=" + child);
                continue;
            }

            float hotseatAdjustedBorderSpace =
                    launcherDp.getHotseatAdjustedBorderSpaceForBubbleBar(child.getContext());
            float hotseatIconCenter;
            if (bubbleBarHasBubbles() && hotseatAdjustedBorderSpace != 0) {
                hotseatIconCenter = hotseatPadding.left + hotseatCellSize
                        + (hotseatCellSize + hotseatAdjustedBorderSpace) * positionInHotseat
                        + hotseatCellSize / 2f;
            } else {
                hotseatIconCenter = hotseatPadding.left
                        + (hotseatCellSize + borderSpacing) * positionInHotseat
                        + hotseatCellSize / 2f;
            }
            float childCenter = (child.getLeft() + child.getRight()) / 2f;
            childCenter += ((Reorderable) child).getTranslateDelegate().getTranslationX(
                    INDEX_TASKBAR_PINNING_ANIM).getValue();
            float toX = hotseatIconCenter - childCenter;
            if (child instanceof Reorderable) {
                MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();
                setter.setFloat(mtd.getTranslationX(INDEX_TASKBAR_ALIGNMENT_ANIM),
                        MULTI_PROPERTY_VALUE, toX, interpolator);
                setter.setFloat(mtd.getTranslationY(INDEX_TASKBAR_ALIGNMENT_ANIM),
                        MULTI_PROPERTY_VALUE, mTaskbarBottomMargin, interpolator);
            } else {
                setter.setFloat(child, VIEW_TRANSLATE_X, toX, interpolator);
                setter.setFloat(child, VIEW_TRANSLATE_Y, mTaskbarBottomMargin, interpolator);
            }
            setter.setFloat(child, SCALE_PROPERTY, scaleUp, interpolator);
        }

        AnimatorPlaybackController controller = setter.createPlaybackController();
        mOnControllerPreCreateCallback = () -> controller.setPlayFraction(0);
        return controller;
    }

    private boolean bubbleBarHasBubbles() {
        return mControllers.bubbleControllers.isPresent()
                && mControllers.bubbleControllers.get().bubbleBarViewController.hasBubbles();
    }

    public void onRotationChanged(DeviceProfile deviceProfile) {
        if (!mControllers.uiController.isIconAlignedWithHotseat()) {
            // We only translate on rotation when icon is aligned with hotseat
            return;
        }
        int taskbarWindowSize;
        if (mActivity.isPhoneMode()) {
            taskbarWindowSize = mActivity.getResources().getDimensionPixelSize(
                    mActivity.isThreeButtonNav()
                            ? R.dimen.taskbar_phone_size
                            : R.dimen.taskbar_stashed_size);
        } else {
            taskbarWindowSize = deviceProfile.taskbarHeight + deviceProfile.getTaskbarOffsetY();
        }
        mActivity.setTaskbarWindowSize(taskbarWindowSize);
        mTaskbarNavButtonTranslationY.updateValue(-deviceProfile.getTaskbarOffsetY());
    }

    /**
     * Maps the given operator to all the top-level children of TaskbarView.
     */
    public void mapOverItems(LauncherBindableItemsContainer.ItemOperator op) {
        mTaskbarView.mapOverItems(op);
    }

    /**
     * Returns the first icon to match the given parameter, in priority from:
     * 1) Icons directly on Taskbar
     * 2) FolderIcon of the Folder containing the given icon
     * 3) All Apps button
     */
    public View getFirstIconMatch(Predicate<ItemInfo> matcher) {
        Predicate<ItemInfo> collectionMatcher = ItemInfoMatcher.forFolderMatch(matcher);
        return mTaskbarView.getFirstMatch(matcher, collectionMatcher);
    }

    /**
     * Returns whether the given MotionEvent, *in screen coorindates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        return mTaskbarView.isEventOverAnyItem(ev);
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarViewController:");

        mTaskbarIconAlpha.dump(
                prefix + "\t",
                pw,
                "mTaskbarIconAlpha",
                "ALPHA_INDEX_HOME",
                "ALPHA_INDEX_KEYGUARD",
                "ALPHA_INDEX_STASH",
                "ALPHA_INDEX_RECENTS_DISABLED",
                "ALPHA_INDEX_NOTIFICATION_EXPANDED",
                "ALPHA_INDEX_ASSISTANT_INVOKED",
                "ALPHA_INDEX_IME_BUTTON_NAV",
                "ALPHA_INDEX_SMALL_SCREEN");

        mModelCallbacks.dumpLogs(prefix + "\t", pw);
    }

    /** Called when there's a change in running apps to update the UI. */
    public void commitRunningAppsToUI() {
        mModelCallbacks.commitRunningAppsToUI();
    }

    /** Call TaskbarModelCallbacks to update running apps. */
    public void updateRunningApps() {
        mModelCallbacks.updateRunningApps();
    }

}
