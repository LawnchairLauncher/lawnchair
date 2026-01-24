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

import static android.animation.LayoutTransition.APPEARING;
import static android.animation.LayoutTransition.CHANGE_APPEARING;
import static android.animation.LayoutTransition.CHANGE_DISAPPEARING;
import static android.animation.LayoutTransition.DISAPPEARING;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.DesktopModeFlags.ENABLE_TASKBAR_OVERFLOW;
import static android.window.DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION;

import static com.android.app.animation.Interpolators.EMPHASIZED;
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
import static com.android.launcher3.taskbar.bubbles.BubbleBarView.FADE_IN_ANIM_ALPHA_DURATION_MS;
import static com.android.launcher3.taskbar.bubbles.BubbleBarView.FADE_OUT_ANIM_POSITION_DURATION_MS;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_BAR_ANIM;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_NAV_BAR_ANIM;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_ALIGNMENT_ANIM;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_PINNING_ANIM;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_REVEAL_ANIM;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.graphics.Rect;
import android.util.FloatProperty;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
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
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.TaskItemInfo;
import com.android.launcher3.taskbar.bubbles.BubbleBarController;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.taskbar.customization.TaskbarAllAppsButtonContainer;
import com.android.launcher3.taskbar.customization.TaskbarDividerContainer;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.SandboxContext;
import com.android.launcher3.views.IconButtonView;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SingleTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Handles properties/data collection, then passes the results to TaskbarView to render.
 */
public class TaskbarViewController implements TaskbarControllers.LoggableTaskbarController,
        BubbleBarController.BubbleBarLocationListener {

    private static final String TAG = "TaskbarViewController";

    private static final Runnable NO_OP = () -> { };

    public static long TRANSLATION_X_FOR_BUBBLEBAR_ANIM_DURATION_MS = 250;

    public static final int ALPHA_INDEX_HOME = 0;
    public static final int ALPHA_INDEX_KEYGUARD = 1;
    public static final int ALPHA_INDEX_STASH = 2;
    public static final int ALPHA_INDEX_RECENTS_DISABLED = 3;
    public static final int ALPHA_INDEX_NOTIFICATION_EXPANDED = 4;
    public static final int ALPHA_INDEX_ASSISTANT_INVOKED = 5;
    public static final int ALPHA_INDEX_SMALL_SCREEN = 6;
    public static final int ALPHA_INDEX_BUBBLE_BAR = 7;
    public static final int ALPHA_INDEX_RECREATE = 8;

    private static final int NUM_ALPHA_CHANNELS = 9;

    /** Only used for animation purposes, to position the divider between two item indices. */
    public static final float DIVIDER_VIEW_POSITION_OFFSET = 0.5f;

    /** Used if an unexpected edge case is hit in {@link #getPositionInHotseat}. */
    private static final float ERROR_POSITION_IN_HOTSEAT_NOT_FOUND = -100;

    private static final int TRANSITION_DELAY = 50;
    static final int TRANSITION_DEFAULT_DURATION = 500;
    private static final int TRANSITION_FADE_IN_DURATION = 167;
    private static final int TRANSITION_FADE_OUT_DURATION = 83;

    private final TaskbarActivityContext mActivity;
    private @Nullable TaskbarDragLayerController mDragLayerController;
    private final TaskbarView mTaskbarView;
    private final MultiValueAlpha mTaskbarIconAlpha;
    private final AnimatedFloat mTaskbarIconScaleForStash = new AnimatedFloat(this::updateScale);
    public final AnimatedFloat mTaskbarIconTranslationYForHome = new AnimatedFloat(
            this::updateTranslationY);
    private final AnimatedFloat mTaskbarIconTranslationYForStash = new AnimatedFloat(
            this::updateTranslationY);

    private final AnimatedFloat mTaskbarIconScaleForPinning = new AnimatedFloat(
            this::updateTaskbarIconsScale);

    private final AnimatedFloat mTaskbarIconTranslationXForPinning = new AnimatedFloat(
            () -> updateTaskbarIconTranslationXForPinning());

    private final AnimatedFloat mIconsTranslationXForNavbar = new AnimatedFloat(
            this::updateTranslationXForNavBar);

    private final AnimatedFloat mTranslationXForBubbleBar = new AnimatedFloat(
            this::updateTranslationXForBubbleBar);

    private final TransitionEndBoundsChangedNotifier mTransitionEndBoundsChangedNotifier =
            new TransitionEndBoundsChangedNotifier();

    @Nullable
    private Animator mTaskbarShiftXAnim;
    @Nullable
    private BubbleBarLocation mCurrentBubbleBarLocation;
    @Nullable
    private BubbleControllers mBubbleControllers = null;
    @Nullable
    private ObjectAnimator mTranslationXAnimation;

    private final AnimatedFloat mTaskbarIconTranslationYForPinning = new AnimatedFloat(
            this::updateTranslationY);


    private AnimatedFloat mTaskbarNavButtonTranslationY;
    private AnimatedFloat mTaskbarNavButtonTranslationYForInAppDisplay;
    private float mTaskbarIconTranslationYForSwipe;
    private float mTaskbarIconTranslationYForSpringOnStash;

    private int mTaskbarBottomMargin;
    private final int mStashedHandleHeight;

    private final TaskbarModelCallbacks mModelCallbacks;

    // Initialized in init.
    private TaskbarControllers mControllers;

    private final View.OnLayoutChangeListener mTaskbarViewLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (!ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION.isTrue()) {
                    // update shiftX is handled with the animation at the end of the method
                    updateTaskbarIconTranslationXForPinning(/* updateShiftXForBubbleBar = */ false);
                }
                if (mBubbleControllers == null) return;
                mControllers.navbarButtonsViewController.onLayoutsUpdated();
                adjustTaskbarXForBubbleBar();
            };

    // Animation to align icons with Launcher, created lazily. This allows the controller to be
    // active only during the animation and does not need to worry about layout changes.
    private AnimatorPlaybackController mIconAlignControllerLazy = null;
    private Runnable mOnControllerPreCreateCallback = NO_OP;

    // Stored here as signals to determine if the mIconAlignController needs to be recreated.
    private boolean mIsIconAlignedWithHotseat;
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

    private final TaskbarRunningAppStateAnimationController mRunningStateController;

    public TaskbarViewController(TaskbarActivityContext activity, TaskbarView taskbarView) {
        mActivity = activity;
        mTransientTaskbarDp = mActivity.getTransientTaskbarDeviceProfile();
        mPersistentTaskbarDp = mActivity.getPersistentTaskbarDeviceProfile();
        mTransientIconSize = mTransientTaskbarDp.getTaskbarProfile().getIconSize();
        mPersistentIconSize = mPersistentTaskbarDp.getTaskbarProfile().getIconSize();
        mTaskbarView = taskbarView;
        mTaskbarIconAlpha = new MultiValueAlpha(mTaskbarView, NUM_ALPHA_CHANNELS);
        mTaskbarIconAlpha.setUpdateVisibility(true);
        mModelCallbacks = TaskbarModelCallbacksFactory.newInstance(mActivity)
                .create(mActivity, mTaskbarView);
        mTaskbarBottomMargin = activity.getDeviceProfile().getTaskbarProfile().getBottomMargin();
        mStashedHandleHeight = activity.getResources()
                .getDimensionPixelSize(R.dimen.taskbar_stashed_handle_height);

        mIsRtl = Utilities.isRtl(mTaskbarView.getResources());
        mTaskbarLeftRightMargin = mActivity.getResources().getDimensionPixelSize(
                R.dimen.transient_taskbar_padding);
        mRunningStateController = new TaskbarRunningAppStateAnimationController(mActivity);
    }

    /**
     * Init of taskbar view controller.
     */
    public void init(TaskbarControllers controllers, AnimatorSet startAnimation) {
        mControllers = controllers;
        controllers.bubbleControllers.ifPresent(bc -> mBubbleControllers = bc);

        if (startAnimation != null) {
            MultiPropertyFactory<View>.MultiProperty multiProperty =
                    mTaskbarIconAlpha.get(ALPHA_INDEX_RECREATE);
            multiProperty.setValue(0f);
            Animator animator = multiProperty.animateToValue(1f);
            animator.setInterpolator(EMPHASIZED);
            startAnimation.play(animator);
        }

        mTaskbarView.init(TaskbarViewCallbacksFactory.newInstance(mActivity).create(
                mActivity, mControllers, mTaskbarView));
        // Pinning popup feature availability depends on taskbar controllers, wait for the
        // controllers state initialization before evaluating the feature.
        mControllers.runAfterInit(mTaskbarView::updatePinningPopupEventHandlers);
        mTaskbarView.getLayoutParams().height = mActivity.isPhoneMode()
                ? mActivity.getResources().getDimensionPixelSize(R.dimen.taskbar_phone_size)
                : mActivity.getDeviceProfile().getTaskbarProfile().getHeight();

        mTaskbarIconScaleForStash.updateValue(1f);
        float pinningValue =
                mActivity.isTransientTaskbar() ? PINNING_TRANSIENT : PINNING_PERSISTENT;
        mTaskbarIconScaleForPinning.updateValue(pinningValue);
        mTaskbarIconTranslationYForPinning.updateValue(pinningValue);
        mTaskbarIconTranslationXForPinning.updateValue(pinningValue);

        mModelCallbacks.init(controllers);
        if (mActivity.isUserSetupComplete()
                && !(mActivity.getApplicationContext() instanceof SandboxContext)) {
            // Only load the callbacks if user setup is completed
            controllers.runAfterInit(() -> LauncherAppState.getInstance(mActivity).getModel()
                    .addCallbacksAndLoad(mModelCallbacks));
        }
        mTaskbarNavButtonTranslationY =
                controllers.navbarButtonsViewController.getTaskbarNavButtonTranslationY();
        mTaskbarNavButtonTranslationYForInAppDisplay = controllers.navbarButtonsViewController
                .getTaskbarNavButtonTranslationYForInAppDisplay();
        mDragLayerController = controllers.taskbarDragLayerController;
        mActivity.addOnDeviceProfileChangeListener(mDeviceProfileChangeListener);

        if (ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
            // This gets modified in NavbarButtonsViewController, but the initial value it reads
            // may be incorrect since it's state gets destroyed on taskbar recreate, so reset here
            mTaskbarIconAlpha.get(ALPHA_INDEX_SMALL_SCREEN).setValue(
                    mActivity.isPhoneMode() ? 0 : 1);
        }
        if (enableTaskbarPinning()) {
            mTaskbarView.addOnLayoutChangeListener(mTaskbarViewLayoutChangeListener);
        }
    }

    /**
     * Called whenever a new ui controller is set.
     */
    public void onUiControllerChanged() {
        // Pinning availability may depend on UI state when home has "locked" pinned taskbar.
        mTaskbarView.updatePinningPopupEventHandlers();
    }

    /** Adjusts start aligned taskbar layout accordingly to the bubble bar position. */
    @Override
    public void onBubbleBarLocationUpdated(BubbleBarLocation location) {
        updateCurrentBubbleBarLocation(location);
        if (mActivity.isTransientTaskbar()) {
            translateTaskbarXForBubbleBar(/* animate= */ false);
        } else if (mActivity.shouldStartAlignTaskbar()) {
            cancelTaskbarShiftAnimation();
            // reset translation x, taskbar will position icons with the updated location
            mIconsTranslationXForNavbar.updateValue(0);
            mTaskbarView.onBubbleBarLocationUpdated(location);
        }
    }

    /** Animates start aligned taskbar accordingly to the bubble bar position. */
    @Override
    public void onBubbleBarLocationAnimated(BubbleBarLocation location) {
        boolean locationUpdated = updateCurrentBubbleBarLocation(location);
        if (mActivity.isTransientTaskbar()) {
            translateTaskbarXForBubbleBar(/* animate= */ true);
        } else if (locationUpdated && mActivity.shouldStartAlignTaskbar()) {
            cancelTaskbarShiftAnimation();
            float translationX = mTaskbarView.getTranslationXForBubbleBarPosition(location);
            mTaskbarShiftXAnim = createTaskbarIconsShiftAnimator(translationX);
            mTaskbarShiftXAnim.start();
        }
    }

    private void translateTaskbarXForBubbleBar(boolean animate) {
        cancelCurrentTranslationXAnimation();
        if (!mActivity.isTransientTaskbar()) return;
        int shiftX = getTransientTaskbarShiftXForBubbleBar();
        if (animate) {
            mTranslationXAnimation = mTranslationXForBubbleBar.animateToValue(shiftX);
            mTranslationXAnimation.setInterpolator(EMPHASIZED);
            mTranslationXAnimation.setDuration(TRANSLATION_X_FOR_BUBBLEBAR_ANIM_DURATION_MS);
            mTranslationXAnimation.start();
        } else {
            mTranslationXForBubbleBar.updateValue(shiftX);
        }
    }

    private void cancelCurrentTranslationXAnimation() {
        if (mTranslationXAnimation != null) {
            if (mTranslationXAnimation.isRunning()) {
                mTranslationXAnimation.cancel();
            }
            mTranslationXAnimation = null;
        }
    }

    private int getTransientTaskbarShiftXForBubbleBar() {
        if (mBubbleControllers == null || !mActivity.isTransientTaskbar()) {
            return 0;
        }
        return mBubbleControllers.bubbleBarViewController
                .getTransientTaskbarTranslationXForBubbleBar(mCurrentBubbleBarLocation);
    }

    /** Updates the mCurrentBubbleBarLocation, returns {@code} true if location is updated. */
    private boolean updateCurrentBubbleBarLocation(BubbleBarLocation location) {
        if (mCurrentBubbleBarLocation == location || location == null) {
            return false;
        } else {
            mCurrentBubbleBarLocation = location;
            return true;
        }
    }

    private void cancelTaskbarShiftAnimation() {
        if (mTaskbarShiftXAnim != null) {
            mTaskbarShiftXAnim.cancel();
        }
    }

    /**
     * Announcement for Accessibility when Taskbar stashes/unstashes.
     */
    public void announceForAccessibility() {
        mTaskbarView.announceAccessibilityChanges();
    }

    /**
     * Called with destroying Taskbar with animation.
     */
    public void onDestroyAnimation(AnimatorSet animatorSet) {
        animatorSet.play(
                mTaskbarIconAlpha.get(TaskbarViewController.ALPHA_INDEX_RECREATE).animateToValue(
                        0f));
    }

    public void onDestroy() {
        if (enableTaskbarPinning()) {
            mTaskbarView.removeOnLayoutChangeListener(mTaskbarViewLayoutChangeListener);
        }
        LauncherAppState.getInstance(mActivity).getModel().removeCallbacks(mModelCallbacks);
        mActivity.removeOnDeviceProfileChangeListener(mDeviceProfileChangeListener);
        mRunningStateController.onDestroy();
    }

    /**
     * Gets the taskbar {@link View.Visibility visibility}.
     */
    public int getTaskbarVisibility() {
        return mTaskbarView.getVisibility();
    }

    public boolean areIconsVisible() {
        return mTaskbarView.areIconsVisible();
    }

    public MultiPropertyFactory<View> getTaskbarIconAlpha() {
        return mTaskbarIconAlpha;
    }

    /** Creates a ModelWriter for updating model properties */
    public ModelWriter getModelWriter() {
        return LauncherAppState.getInstance(mActivity).getModel()
                .getWriter(false, mActivity.getCellPosMapper(), mModelCallbacks);
    }

    /**
     * Should be called when the recents button is disabled, so we can hide Taskbar icons as well.
     */
    public void setRecentsButtonDisabled(boolean isDisabled) {
        // TODO: check TaskbarStashController#supportsStashing(), to stash instead of setting alpha.
        mTaskbarIconAlpha.get(ALPHA_INDEX_RECENTS_DISABLED).animateToValue(isDisabled ? 0 : 1)
                .start();
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

    @VisibleForTesting
    int getMaxNumIconViews() {
        return mTaskbarView.getMaxNumIconViews();
    }

    public Rect getTransientTaskbarIconLayoutBounds() {
        return mTaskbarView.getTransientTaskbarIconLayoutBounds();
    }

    public Rect getTransientTaskbarIconLayoutBoundsInParent() {
        return mTaskbarView.getTransientTaskbarIconLayoutBoundsInParent();
    }

    public View[] getIconViews() {
        return mTaskbarView.getIconViews();
    }

    public View getAllAppsButtonView() {
        return mTaskbarView.getAllAppsButtonContainer();
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
        TaskbarSharedState sharedState = mControllers.getSharedState();
        if (sharedState != null && sharedState.startTaskbarVariantIsTransient) {
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

    void updateTaskbarIconTranslationXForPinning() {
        updateTaskbarIconTranslationXForPinning(/* updateShiftXForBubbleBar = */ true);
    }

    void updateTaskbarIconTranslationXForPinning(boolean updateShiftXForBubbleBar) {
        View[] iconViews = mTaskbarView.getIconViews();
        float scale = mTaskbarIconTranslationXForPinning.value;
        float transientTaskbarAllAppsOffset = mActivity.getResources().getDimension(
                mTaskbarView.getAllAppsButtonContainer().getAllAppsButtonTranslationXOffset(true));
        float persistentTaskbarAllAppsOffset = mActivity.getResources().getDimension(
                mTaskbarView.getAllAppsButtonContainer().getAllAppsButtonTranslationXOffset(false));
        if (mBubbleControllers != null && updateShiftXForBubbleBar) {
            cancelCurrentTranslationXAnimation();
            int translationXForTransientTaskbar = mBubbleControllers.bubbleBarViewController
                    .getTransientTaskbarTranslationXForBubbleBar(mCurrentBubbleBarLocation);
            float currentTranslationXForTransientTaskbar = mapRange(scale,
                    translationXForTransientTaskbar, 0);
            mTranslationXForBubbleBar.updateValue(currentTranslationXForTransientTaskbar);
        }
        float allAppIconTranslateRange = mapRange(scale, transientTaskbarAllAppsOffset,
                persistentTaskbarAllAppsOffset);
        // Task icons are laid out so the taskbar content is centered. The taskbar width (used for
        // centering taskbar icons) depends on the all apps button X translation, and is different
        // for persistent and transient taskbar. If the offset used for current taskbar layout is
        // different than the offset used in final taskbar state, the icons may jump when the
        // animation completes, and the taskbar is replaced. Adjust item transform to account for
        // this mismatch.
        float sizeDiffTranslationRange =
                mapRange(scale,
                        (mTaskbarView.getAllAppsButtonTranslationXOffsetUsedForLayout()
                                - transientTaskbarAllAppsOffset) / 2,
                        (mTaskbarView.getAllAppsButtonTranslationXOffsetUsedForLayout()
                                - persistentTaskbarAllAppsOffset) / 2);

        // no x translation required when all apps button is the only icon in taskbar.
        if (iconViews.length <= 1) {
            allAppIconTranslateRange = 0f;
        }

        if (mIsRtl) {
            allAppIconTranslateRange *= -1;
            sizeDiffTranslationRange *= -1;
        }

        if (!mTaskbarView.canTransitionToTransientTaskbar()) {
            mTaskbarView.getAllAppsButtonContainer()
                    .setTranslationXForTaskbarAllAppsIcon(allAppIconTranslateRange);
            return;
        }

        float finalMarginScale = mapRange(scale, 0f, mTransientIconSize - mPersistentIconSize);

        // The index of the "middle" icon which will be used as a index from which the icon margins
        // will be scaled. If number of icons is even, using the middle point between indices of two
        // central icons.
        float middleIndex = (iconViews.length - 1) / 2.0f;
        for (int iconIndex = 0; iconIndex < iconViews.length; iconIndex++) {
            View iconView = iconViews[iconIndex];
            MultiTranslateDelegate translateDelegate =
                    ((Reorderable) iconView).getTranslateDelegate();
            translateDelegate.getTranslationX(INDEX_TASKBAR_PINNING_ANIM).setValue(
                    finalMarginScale * (middleIndex - iconIndex) + sizeDiffTranslationRange);

            if (iconView.equals(mTaskbarView.getAllAppsButtonContainer())) {
                mTaskbarView.getAllAppsButtonContainer().setTranslationXForTaskbarAllAppsIcon(
                        allAppIconTranslateRange);
            }
        }
    }

    /**
     * Calculates visual taskbar view width.
     */
    public float getCurrentVisualTaskbarWidth() {
        View[] iconViews = mTaskbarView.getIconViews();
        if (iconViews.length == 0) {
            return 0;
        }

        float left = iconViews[0].getX();

        int rightIndex = iconViews.length - 1;
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

    private void updateTranslationXForNavBar() {
        updateIconViewsTranslationX(INDEX_NAV_BAR_ANIM, mIconsTranslationXForNavbar.value);
    }

    private void updateTranslationXForBubbleBar() {
        float translationX = mTranslationXForBubbleBar.value;
        updateIconViewsTranslationX(INDEX_BUBBLE_BAR_ANIM, translationX);
        if (mDragLayerController != null) {
            mDragLayerController.setTranslationXForBubbleBar(translationX);
        }
    }

    private void updateIconViewsTranslationX(int translationXChannel, float translationX) {
        View[] iconViews = mTaskbarView.getIconViews();
        for (View iconView : iconViews) {
            MultiTranslateDelegate translateDelegate =
                    ((Reorderable) iconView).getTranslateDelegate();
            translateDelegate.getTranslationX(translationXChannel).setValue(translationX);
        }
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
                    mTransientTaskbarDp.getTaskbarProfile().getBottomMargin() + (
                            mTransientTaskbarDp.getTaskbarProfile().getHeight()
                            - mTaskbarView.getTransientTaskbarIconLayoutBounds().bottom)
                            - (mPersistentTaskbarDp.getTaskbarProfile().getHeight()
                                    - mTransientTaskbarDp.getTaskbarProfile().getIconSize()) / 2f;
            taskbarIconTranslationYForPinningValue = mapRange(scale, 0f, transY);
        } else {
            float transY =
                    -mTransientTaskbarDp.getTaskbarProfile().getBottomMargin() + (
                            mPersistentTaskbarDp.getTaskbarProfile().getHeight()
                            - mTaskbarView.getTransientTaskbarIconLayoutBounds().bottom)
                            - (mTransientTaskbarDp.getTaskbarProfile().getHeight()
                                    - mTransientTaskbarDp.getTaskbarProfile().getIconSize()) / 2f;
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
        return mTaskbarView.getTaskbarDividerViewContainer();
    }

    /**
     * Updates which icons are marked as running or minimized given the Sets of currently running
     * and minimized tasks.
     */
    public void updateIconViewsRunningStates() {
        for (View iconView : getIconViews()) {
            if (iconView instanceof BubbleTextView btv) {
                updateRunningState(btv);
                if (shouldUpdateIconContentDescription(btv)) {
                    btv.setContentDescription(
                            btv.getContentDescription() + " " + btv.getIconStateDescription());
                }
            }
        }
    }

    private boolean shouldUpdateIconContentDescription(BubbleTextView btv) {
        boolean isInDesktopMode =
                mControllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar(
                        DEFAULT_DISPLAY);
        boolean isAllAppsButton = btv instanceof TaskbarAllAppsButtonContainer;
        boolean isDividerButton = btv instanceof TaskbarDividerContainer;
        return isInDesktopMode && !isAllAppsButton && !isDividerButton;
    }

    /**
     * @return A set of Task ids shown in the taskbar - includes task ID for running tasks of pinned
     *         apps, and standalone running tasks.
     */
    protected Set<Integer> getShownTaskIds() {
        if (!ENABLE_TASKBAR_OVERFLOW.isTrue()) {
            return Collections.emptySet();
        }

        Set<Integer> shownTasks = new HashSet<>();
        for (View iconView : getIconViews()) {
            if (iconView instanceof BubbleTextView btv) {
                if (btv.getTag() instanceof TaskItemInfo itemInfo) {
                    shownTasks.add(itemInfo.getTaskId());
                } else if (btv.getTag() instanceof SingleTask task) {
                    shownTasks.add(task.getTask().getKey().id);
                }
            }
        }
        return shownTasks;
    }

    private void updateRunningState(BubbleTextView btv) {
        mRunningStateController.updateRunningState(
                btv,
                getRunningAppState(btv),
                /* animate = */ mTaskbarView.getLayoutTransition() != null);
    }

    private BubbleTextView.RunningAppState getRunningAppState(BubbleTextView btv) {
        Object tag = btv.getTag();
        if (tag instanceof TaskItemInfo itemInfo) {
            return mControllers.taskbarRecentAppsController.getRunningAppState(
                    itemInfo.getTaskId());
        }
        if (tag instanceof SingleTask singleTask) {
            return mControllers.taskbarRecentAppsController.getRunningAppState(
                    singleTask.getTask().key.id);
        }
        return BubbleTextView.RunningAppState.NOT_RUNNING;
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

    void notifyIconLayoutBoundsChanged() {
        final LayoutTransition layoutTransition = mTaskbarView.getLayoutTransition();
        if (layoutTransition != null && layoutTransition.isRunning()) {
            // Defers notify until after transitions finish.
            mTransitionEndBoundsChangedNotifier.mIsCanceled = false;
        } else {
            mControllers.uiController.onIconLayoutBoundsChanged();
        }
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
        boolean isIconAlignedWithHotseat = mControllers.uiController.isIconAlignedWithHotseat();
        boolean isStashed = mControllers.taskbarStashController.isStashed();
        // Re-create animation when any of these values change.
        if (mIconAlignControllerLazy == null
                || mIsHotseatIconOnTopWhenAligned != isHotseatIconOnTopWhenAligned
                || mIsIconAlignedWithHotseat != isIconAlignedWithHotseat
                || mIsStashed != isStashed) {
            mIsHotseatIconOnTopWhenAligned = isHotseatIconOnTopWhenAligned;
            mIsIconAlignedWithHotseat = isIconAlignedWithHotseat;
            mIsStashed = isStashed;

            final LayoutTransition layoutTransition = mTaskbarView.getLayoutTransition();
            if (layoutTransition != null && layoutTransition.isRunning()) {
                mTransitionEndBoundsChangedNotifier.mIsCanceled = true;
                layoutTransition.cancel();
            }
            mIconAlignControllerLazy = createIconAlignmentController(launcherDp);
        }
        mIconAlignControllerLazy.setPlayFraction(alignmentRatio);
        if (alignmentRatio <= 0 || alignmentRatio >= 1) {
            // Cleanup lazy controller so that it is created again in next animation
            mIconAlignControllerLazy = null;
        }
    }

    /**
     * Resets the icon alignment controller so that it can be recreated again later, and updates
     * the list of icons shown in the taskbar if the bubble bar visibility changes the taskbar
     * overflow state.
     */
    void adjustTaskbarForBubbleBar() {
        mIconAlignControllerLazy = null;
        if (mTaskbarView.updateMaxNumIcons()) {
            commitRunningAppsToUI();
        }
        adjustTaskbarXForBubbleBar();
    }

    private void adjustTaskbarXForBubbleBar() {
        if (mBubbleControllers != null && mActivity.isTransientTaskbar()) {
            translateTaskbarXForBubbleBar(/* animate= */ true);
        }
    }

    /**
     * Creates an animation for aligning the Taskbar icons with the provided Launcher device profile
     */
    private AnimatorPlaybackController createIconAlignmentController(DeviceProfile launcherDp) {
        PendingAnimation setter = new PendingAnimation(100);
        // icon alignment not needed for pinned taskbar.
        if (mActivity.isPinnedTaskbar()) {
            return setter.createPlaybackController();
        }
        mOnControllerPreCreateCallback.run();
        DeviceProfile taskbarDp = mActivity.getDeviceProfile();
        Rect hotseatPadding = launcherDp.getHotseatLayoutPadding(mActivity);
        boolean isTransientTaskbar = mActivity.isTransientTaskbar();

        float scaleUp = ((float) launcherDp.iconSizePx)
                / taskbarDp.getTaskbarProfile().getIconSize();
        int borderSpacing = launcherDp.hotseatBorderSpace;
        int hotseatCellSize = DeviceProfile.calculateCellWidth(
                launcherDp.getDeviceProperties().getAvailableWidthPx()
                        - hotseatPadding.left
                        - hotseatPadding.right,
                borderSpacing,
                launcherDp.numShownHotseatIcons
        );

        boolean isToHome = mControllers.uiController.isIconAlignedWithHotseat();
        boolean isDeviceLocked = mControllers.taskbarStashController.isDeviceLocked();
        // If Hotseat is not the top element, Taskbar should maintain in-app state as it fades out,
        // or fade in while already in in-app state.
        Interpolator interpolator = mIsHotseatIconOnTopWhenAligned ? LINEAR : FINAL_FRAME;

        int offsetY =
                isDeviceLocked ? taskbarDp.getTaskbarOffsetY() : launcherDp.getTaskbarOffsetY();
        setter.setFloat(mTaskbarIconTranslationYForHome, VALUE, -offsetY, interpolator);
        setter.setFloat(mTaskbarNavButtonTranslationY, VALUE, -offsetY, interpolator);
        setter.setFloat(mTaskbarNavButtonTranslationYForInAppDisplay, VALUE, offsetY, interpolator);
        if (mBubbleControllers != null
                && mCurrentBubbleBarLocation != null
                && mActivity.isTransientTaskbar()) {
            int offsetX = mBubbleControllers.bubbleBarViewController
                    .getTransientTaskbarTranslationXForBubbleBar(mCurrentBubbleBarLocation);
            if (offsetX != 0) {
                // if taskbar should be adjusted for the bubble bar adjust the taskbar translation
                mTranslationXForBubbleBar.updateValue(offsetX);
                setter.setFloat(mTranslationXForBubbleBar, VALUE, 0, interpolator);
            }
        }
        int collapsedHeight = mActivity.getDefaultTaskbarWindowSize();
        int expandedHeight = Math.max(collapsedHeight,
                taskbarDp.getTaskbarProfile().getHeight() + offsetY);
        setter.addOnFrameListener(anim -> mActivity.setTaskbarWindowSize(
                anim.getAnimatedFraction() > 0 ? expandedHeight : collapsedHeight));

        mTaskbarBottomMargin = isTransientTaskbar
                ? mTransientTaskbarDp.getTaskbarProfile().getBottomMargin()
                : mPersistentTaskbarDp.getTaskbarProfile().getBottomMargin();

        int firstRecentTaskIndex = -1;
        int hotseatNavBarTranslationX = 0;
        if (mCurrentBubbleBarLocation != null) {
            boolean isBubblesOnLeft = mCurrentBubbleBarLocation
                    .isOnLeft(mTaskbarView.isLayoutRtl());
            hotseatNavBarTranslationX = taskbarDp
                    .getHotseatTranslationXForNavBar(mActivity, isBubblesOnLeft);
        }

        int ignoreCount = mTaskbarView.getIgnoreTaskbarIconCount();

        for (int i = 0; i < mTaskbarView.getChildCount(); i++) {
            View child = mTaskbarView.getChildAt(i);
            boolean isAllAppsButton = child == mTaskbarView.getAllAppsButtonContainer();
            boolean isTaskbarDividerView = child == mTaskbarView.getTaskbarDividerViewContainer();
            boolean isTaskbarOverflowView = child == mTaskbarView.getTaskbarOverflowView();
            boolean isRecentTask = child.getTag() instanceof GroupTask;
            boolean isRtl = Utilities.isRtl(child.getResources());

            // TODO(b/343522351): show recents on the home screen.
            final boolean isRecentsInHotseat = false;
            if (!mIsHotseatIconOnTopWhenAligned) {
                // When going to home, the EMPHASIZED interpolator in TaskbarLauncherStateController
                // plays iconAlignment to 1 really fast, therefore moving the fading towards the end
                // to avoid icons disappearing rather than fading out visually.
                setter.setViewAlpha(child, 0, Interpolators.clampToProgress(LINEAR, 0.8f, 1f));
            } else if ((isAllAppsButton && !FeatureFlags.enableAllAppsButtonInHotseat())
                    || (isTaskbarDividerView && enableTaskbarPinning())
                    || (isRecentTask && !isRecentsInHotseat)
                    || isTaskbarOverflowView) {
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
            } else if (((!isRtl && mTaskbarView.getChildCount() - i <= ignoreCount)
                    || (isRtl && i < ignoreCount))
                    && mIsHotseatIconOnTopWhenAligned
                    && !(child instanceof IconButtonView)) {
                setter.addFloat(child, VIEW_ALPHA, 0f, 1f,
                        isToHome
                                ? Interpolators.clampToProgress(LINEAR, 0f, 0.35f)
                                : mActivity.getDeviceProfile().isQsbInline
                                        ? Interpolators.clampToProgress(LINEAR, 0f, 1f)
                                        : Interpolators.clampToProgress(LINEAR, 0.84f, 1f));
                setter.addOnFrameListener(animator -> AlphaUpdateListener.updateVisibility(child));
            }
            if (child == mTaskbarView.getQsb()) {
                float hotseatIconCenter = isRtl
                        ? launcherDp.getDeviceProperties().getWidthPx() - hotseatPadding.right + borderSpacing
                        + launcherDp.hotseatQsbWidth / 2f
                        : hotseatPadding.left - borderSpacing - launcherDp.hotseatQsbWidth / 2f;
                if (taskbarDp.isQsbInline) {
                    hotseatIconCenter += hotseatNavBarTranslationX;
                }
                float childCenter = (child.getLeft() + child.getRight()) / 2f;
                if (child instanceof Reorderable reorderableChild) {
                    childCenter += reorderableChild.getTranslateDelegate().getTranslationX(
                            INDEX_TASKBAR_PINNING_ANIM).getValue();
                }
                float halfQsbIconWidthDiff =
                        (launcherDp.hotseatQsbWidth - taskbarDp.getTaskbarProfile().getIconSize())
                                / 2f;
                float scale = ((float) taskbarDp.getTaskbarProfile().getIconSize())
                        / launcherDp.getHotseatProfile().getQsbVisualHeight();
                setter.addFloat(child, SCALE_PROPERTY, scale, 1f, interpolator);

                float fromX = isRtl ? -halfQsbIconWidthDiff : halfQsbIconWidthDiff;
                float toX = hotseatIconCenter - childCenter;
                if (child instanceof Reorderable reorderableChild) {
                    MultiTranslateDelegate mtd = reorderableChild.getTranslateDelegate();

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

            int recentTaskIndex = -1;
            if (isRecentTask) {
                if (firstRecentTaskIndex < 0) {
                    firstRecentTaskIndex = i;
                }
                recentTaskIndex = i - firstRecentTaskIndex;
            }
            float positionInHotseat = getPositionInHotseat(taskbarDp.numShownHotseatIcons, child,
                    mIsRtl, isAllAppsButton, isTaskbarDividerView,
                    mTaskbarView.isDividerForRecents(), recentTaskIndex);
            if (positionInHotseat == ERROR_POSITION_IN_HOTSEAT_NOT_FOUND) continue;


            float hotseatIconCenter;
            if (launcherDp.shouldAdjustHotseatForBubbleBar(child.getContext(),
                    bubbleBarHasBubbles())) {
                float hotseatAdjustedBorderSpace =
                        launcherDp.getHotseatAdjustedBorderSpaceForBubbleBar(child.getContext());
                hotseatIconCenter = hotseatPadding.left + hotseatCellSize
                        + (hotseatCellSize + hotseatAdjustedBorderSpace) * positionInHotseat
                        + hotseatCellSize / 2f;
            } else {
                hotseatIconCenter = hotseatPadding.left
                        + (hotseatCellSize + borderSpacing) * positionInHotseat
                        + hotseatCellSize / 2f;
            }
            hotseatIconCenter += hotseatNavBarTranslationX;
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

    /**
     * Returns the index of the given child relative to its position in hotseat.
     * Examples:
     * -1 is the item before the first hotseat item.
     * -0.5 is between those (e.g. for the divider).
     * {@link #ERROR_POSITION_IN_HOTSEAT_NOT_FOUND} if there's no calculation relative to hotseat.
     */
    @VisibleForTesting
    float getPositionInHotseat(int numShownHotseatIcons, View child, boolean isRtl,
            boolean isAllAppsButton, boolean isTaskbarDividerView, boolean isDividerForRecents,
            int recentTaskIndex) {
        float positionInHotseat;
        // Note that there is no All Apps button in the hotseat,
        // this position is only used as it's convenient for animation purposes.
        float allAppsButtonPositionInHotseat = isRtl
                // Right after all hotseat items.
                // [HHHHHH]|[>A<]
                ? numShownHotseatIcons
                // Right before all hotseat items.
                // [>A<]|[HHHHHH]
                : -1;
        // Note that there are no recent tasks in the hotseat,
        // this position is only used as it's convenient for animation purposes.
        float firstRecentTaskPositionInHotseat = isRtl
                // After all hotseat icons and All Apps button.
                // [HHHHHH][A]|[>R<R]
                ? numShownHotseatIcons + 1
                // Right after all hotseat items.
                // [A][HHHHHH]|[>R<R]
                : numShownHotseatIcons;
        if (isAllAppsButton) {
            positionInHotseat = allAppsButtonPositionInHotseat;
        }  else if (isTaskbarDividerView) {
            // Note that there is no taskbar divider view in the hotseat,
            // this position is only used as it's convenient for animation purposes.
            float relativePosition = isDividerForRecents
                    ? firstRecentTaskPositionInHotseat
                    : allAppsButtonPositionInHotseat;
            positionInHotseat = relativePosition > 0
                    ? relativePosition - DIVIDER_VIEW_POSITION_OFFSET
                    : relativePosition + DIVIDER_VIEW_POSITION_OFFSET;
        } else if (child.getTag() instanceof ItemInfo) {
            positionInHotseat = ((ItemInfo) child.getTag()).screenId;
        } else if (recentTaskIndex >= 0) {
            positionInHotseat = firstRecentTaskPositionInHotseat + recentTaskIndex;
        } else {
            Log.w(TAG, "Unsupported view found in createIconAlignmentController, v=" + child);
            return ERROR_POSITION_IN_HOTSEAT_NOT_FOUND;
        }
        return positionInHotseat;
    }

    private boolean bubbleBarHasBubbles() {
        return mBubbleControllers != null
                && mBubbleControllers.bubbleBarViewController.hasBubbles();
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
            taskbarWindowSize = mActivity.getDefaultTaskbarWindowSize();
        }
        if (mBubbleControllers != null) {
            int bubbleBarMaxHeight = mBubbleControllers.bubbleBarViewController
                    .getBubbleBarWithFlyoutMaximumHeight();
            taskbarWindowSize = Math.max(taskbarWindowSize, bubbleBarMaxHeight);
        }
        mActivity.setTaskbarWindowSize(taskbarWindowSize);
        mTaskbarNavButtonTranslationY.updateValue(-deviceProfile.getTaskbarOffsetY());
    }

    public LauncherBindableItemsContainer getContent() {
        return mModelCallbacks;
    }

    /**
     * Returns the first icon to match the given parameter, in priority from:
     * 1) Icons directly on Taskbar
     * 2) FolderIcon of the Folder containing the given icon
     * 3) All Apps button
     */
    public View getFirstIconMatch(Predicate<ItemInfo> matcher) {
        View icon = mModelCallbacks.getFirstMatch(matcher, ItemInfoMatcher.forFolderMatch(matcher));
        return icon != null ? icon : mTaskbarView.getAllAppsButtonContainer();
    }

    /**
     * Returns whether the given MotionEvent, *in screen coorindates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        return mTaskbarView.isEventOverAnyItem(ev);
    }

    /** Called when there's a change in running apps to update the UI. */
    public void commitRunningAppsToUI() {
        mModelCallbacks.commitRunningAppsToUI();
        if (ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION.isTrue()
                && !mActivity.isTransientTaskbar()
                && mTaskbarView.getLayoutTransition() == null) {
            // Set up after the first commit so that the initial recents do not animate (janky).
            mTaskbarView.setLayoutTransition(createLayoutTransitionForRunningApps());
        }
    }

    private LayoutTransition createLayoutTransitionForRunningApps() {
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.setDuration(TRANSITION_DEFAULT_DURATION);
        layoutTransition.addTransitionListener(new TransitionListener() {

            @Override
            public void startTransition(
                    LayoutTransition transition, ViewGroup container, View view, int type) {
                if (type == APPEARING) {
                    view.setAlpha(0f);
                    view.setScaleX(0f);
                    view.setScaleY(0f);
                } else if (type == DISAPPEARING && view instanceof BubbleTextView btv) {
                    // Running state updates happen after removing this view, so update it here.
                    updateRunningState(btv);
                }
            }

            @Override
            public void endTransition(
                    LayoutTransition transition, ViewGroup container, View view, int type) {
                // Do nothing.
            }
        });
        layoutTransition.addTransitionListener(mTransitionEndBoundsChangedNotifier);

        // Appearing.
        AnimatorSet appearingSet = new AnimatorSet();
        Animator appearingAlphaAnimator = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f);
        appearingAlphaAnimator.setInterpolator(Interpolators.clampToProgress(LINEAR, 0f,
                (float) TRANSITION_FADE_IN_DURATION / TRANSITION_DEFAULT_DURATION));
        Animator appearingScaleAnimator = ObjectAnimator.ofFloat(null, SCALE_PROPERTY, 0f, 1f);
        appearingScaleAnimator.setInterpolator(EMPHASIZED);
        appearingSet.playTogether(appearingAlphaAnimator, appearingScaleAnimator);
        layoutTransition.setAnimator(APPEARING, appearingSet);
        layoutTransition.setStartDelay(APPEARING, TRANSITION_DELAY);

        // Disappearing.
        AnimatorSet disappearingSet = new AnimatorSet();
        Animator disappearingAlphaAnimator = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f);
        disappearingAlphaAnimator.setInterpolator(Interpolators.clampToProgress(LINEAR,
                (float) TRANSITION_DELAY / TRANSITION_DEFAULT_DURATION,
                (float) (TRANSITION_DELAY + TRANSITION_FADE_OUT_DURATION)
                        / TRANSITION_DEFAULT_DURATION));
        Animator disappearingScaleAnimator = ObjectAnimator.ofFloat(null, SCALE_PROPERTY, 1f, 0f);
        disappearingScaleAnimator.setInterpolator(EMPHASIZED);
        disappearingSet.playTogether(disappearingAlphaAnimator, disappearingScaleAnimator);
        layoutTransition.setAnimator(DISAPPEARING, disappearingSet);

        // Change transitions.
        FloatProperty<View> translateXPinning = new FloatProperty<>("translateXPinning") {
            @Override
            public void setValue(View view, float value) {
                getTranslationXForPinning(view).setValue(value);
            }

            @Override
            public Float get(View view) {
                return getTranslationXForPinning(view).getValue();
            }

            private MultiProperty getTranslationXForPinning(View view) {
                return ((Reorderable) view).getTranslateDelegate()
                        .getTranslationX(INDEX_TASKBAR_PINNING_ANIM);
            }
        };
        AnimatorSet changeSet = new AnimatorSet();
        changeSet.playTogether(
                layoutTransition.getAnimator(CHANGE_APPEARING),
                ObjectAnimator.ofFloat(null, translateXPinning, 0f, 1f));

        // Change appearing.
        layoutTransition.setAnimator(CHANGE_APPEARING, changeSet);
        layoutTransition.setInterpolator(CHANGE_APPEARING, EMPHASIZED);

        // Change disappearing.
        layoutTransition.setAnimator(CHANGE_DISAPPEARING, changeSet);
        layoutTransition.setInterpolator(CHANGE_DISAPPEARING, EMPHASIZED);
        layoutTransition.setStartDelay(CHANGE_DISAPPEARING, TRANSITION_DELAY);

        return layoutTransition;
    }

    public boolean isTaskbarInMinimalState() {
        return mTaskbarView.isTaskbarInMinimalState();
    }

    /**
     * To be called when the given Task is updated, so that we can tell TaskbarView to also update.
     * @param task The Task whose e.g. icon changed.
     */
    public void onTaskUpdated(Task task) {
        // Find the icon view(s) that changed.
        for (View view : mTaskbarView.getIconViews()) {
            if (view instanceof BubbleTextView btv
                    && view.getTag() instanceof GroupTask groupTask) {
                if (groupTask.containsTask(task.key.id)) {
                    mTaskbarView.applyGroupTaskToBubbleTextView(btv, groupTask);
                }
            } else if (view instanceof TaskbarOverflowView overflowButton) {
                overflowButton.updateTaskIsShown(task);
            }
        }
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarViewController:");
        pw.println(prefix + "\tignoreTaskbarIconCount=" + mTaskbarView.getIgnoreTaskbarIconCount());
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
                "ALPHA_INDEX_SMALL_SCREEN");

        mModelCallbacks.dumpLogs(prefix + "\t", pw);
    }

    private ObjectAnimator createTaskbarIconsShiftAnimator(float translationX) {
        ObjectAnimator animator = mIconsTranslationXForNavbar.animateToValue(translationX);
        animator.setStartDelay(FADE_OUT_ANIM_POSITION_DURATION_MS);
        animator.setDuration(FADE_IN_ANIM_ALPHA_DURATION_MS);
        animator.setInterpolator(EMPHASIZED);
        return animator;
    }

    private class TransitionEndBoundsChangedNotifier implements TransitionListener {
        private boolean mIsCanceled;

        @Override
        public void startTransition(
                LayoutTransition transition, ViewGroup container, View view, int type) {
            // Do nothing.
        }

        @Override
        public void endTransition(
                LayoutTransition transition, ViewGroup container, View view, int type) {
            if (!transition.isRunning() && !mIsCanceled) {
                mControllers.uiController.onIconLayoutBoundsChanged();
            }
        }
    }
}
