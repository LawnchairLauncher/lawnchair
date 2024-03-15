/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.app.animation.Interpolators.DECELERATE_1_7;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.ALL_APPS_CONTENT;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.Utilities.restoreClipChildrenOnViewTree;
import static com.android.launcher3.Utilities.setClipChildrenOnViewTree;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_BOTTOM_SHEET_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.util.SystemUiController.FLAG_DARK_NAV;
import static com.android.launcher3.util.SystemUiController.FLAG_LIGHT_NAV;
import static com.android.launcher3.util.SystemUiController.UI_STATE_ALL_APPS;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.util.FloatProperty;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.FloatRange;

import com.android.app.animation.Interpolators;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.AllAppsSwipeController;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.ScrollableLayoutManager;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.views.ScrimView;

/**
 * Handles AllApps view transition.
 * 1) Slides all apps view using direct manipulation
 * 2) When finger is released, animate to either top or bottom accordingly.
 * <p/>
 * Algorithm:
 * If release velocity > THRES1, snap according to the direction of movement.
 * If release velocity < THRES1, snap according to either top or bottom depending on whether it's
 * closer to top or closer to the page indicator.
 */
public class AllAppsTransitionController
        implements StateHandler<LauncherState>, OnDeviceProfileChangeListener {
    // This constant should match the second derivative of the animator interpolator.
    public static final float INTERP_COEFF = 1.7f;
    public static final int REVERT_SWIPE_ALL_APPS_TO_HOME_ANIMATION_DURATION_MS = 200;

    private static final float NAV_BAR_COLOR_FORCE_UPDATE_THRESHOLD = 0.1f;
    private static final float SWIPE_DRAG_COMMIT_THRESHOLD =
            1 - AllAppsSwipeController.ALL_APPS_STATE_TRANSITION_MANUAL;

    public static final FloatProperty<AllAppsTransitionController> ALL_APPS_PROGRESS =
            new FloatProperty<AllAppsTransitionController>("allAppsProgress") {

                @Override
                public Float get(AllAppsTransitionController controller) {
                    return controller.mProgress;
                }

                @Override
                public void setValue(AllAppsTransitionController controller, float progress) {
                    controller.setProgress(progress);
                }
            };

    private static final float ALL_APPS_PULL_BACK_TRANSLATION_DEFAULT = 0f;

    public static final FloatProperty<AllAppsTransitionController> ALL_APPS_PULL_BACK_TRANSLATION =
            new FloatProperty<AllAppsTransitionController>("allAppsPullBackTranslation") {

                @Override
                public Float get(AllAppsTransitionController controller) {
                    if (controller.mIsTablet) {
                        return controller.mAppsView.getActiveRecyclerView().getTranslationY();
                    } else {
                        return controller.getAppsViewPullbackTranslationY().getValue();
                    }
                }

                @Override
                public void setValue(AllAppsTransitionController controller, float translation) {
                    if (controller.mIsTablet) {
                        controller.mAppsView.getActiveRecyclerView().setTranslationY(translation);
                        controller.getAppsViewPullbackTranslationY().setValue(
                                ALL_APPS_PULL_BACK_TRANSLATION_DEFAULT);
                    } else {
                        controller.getAppsViewPullbackTranslationY().setValue(translation);
                        controller.mAppsView.getActiveRecyclerView().setTranslationY(
                                ALL_APPS_PULL_BACK_TRANSLATION_DEFAULT);
                    }
                }
            };

    private static final float ALL_APPS_PULL_BACK_ALPHA_DEFAULT = 1f;

    public static final FloatProperty<AllAppsTransitionController> ALL_APPS_PULL_BACK_ALPHA =
            new FloatProperty<AllAppsTransitionController>("allAppsPullBackAlpha") {

                @Override
                public Float get(AllAppsTransitionController controller) {
                    if (controller.mIsTablet) {
                        return controller.mAppsView.getActiveRecyclerView().getAlpha();
                    } else {
                        return controller.getAppsViewPullbackAlpha().getValue();
                    }
                }

                @Override
                public void setValue(AllAppsTransitionController controller, float alpha) {
                    if (controller.mIsTablet) {
                        controller.mAppsView.getActiveRecyclerView().setAlpha(alpha);
                        controller.getAppsViewPullbackAlpha().setValue(
                                ALL_APPS_PULL_BACK_ALPHA_DEFAULT);
                    } else {
                        controller.getAppsViewPullbackAlpha().setValue(alpha);
                        controller.mAppsView.getActiveRecyclerView().setAlpha(
                                ALL_APPS_PULL_BACK_ALPHA_DEFAULT);
                    }
                }
            };

    private static final int INDEX_APPS_VIEW_PROGRESS = 0;
    private static final int INDEX_APPS_VIEW_PULLBACK = 1;
    private static final int APPS_VIEW_INDEX_COUNT = 2;

    private ActivityAllAppsContainerView<Launcher> mAppsView;

    private final Launcher mLauncher;
    private final AnimatedFloat mAllAppScale = new AnimatedFloat(this::onScaleProgressChanged);
    private final int mNavScrimFlag;

    private boolean mIsVerticalLayout;

    // Animation in this class is controlled by a single variable {@link mProgress}.
    // Visually, it represents top y coordinate of the all apps container if multiplied with
    // {@link mShiftRange}.

    // When {@link mProgress} is 0, all apps container is pulled up.
    // When {@link mProgress} is 1, all apps container is pulled down.
    private float mShiftRange;      // changes depending on the orientation
    private float mProgress;        // [0, 1], mShiftRange * mProgress = shiftCurrent

    private ScrimView mScrimView;

    private MultiValueAlpha mAppsViewAlpha;
    private MultiPropertyFactory<View> mAppsViewTranslationY;

    private boolean mIsTablet;

    private boolean mHasScaleEffect;
    private final VibratorWrapper mVibratorWrapper;

    public AllAppsTransitionController(Launcher l) {
        mLauncher = l;
        DeviceProfile dp = mLauncher.getDeviceProfile();
        mProgress = 1f;
        mIsVerticalLayout = dp.isVerticalBarLayout();
        mIsTablet = dp.isTablet;
        mNavScrimFlag = Themes.getAttrBoolean(l, R.attr.isMainColorDark)
                ? FLAG_DARK_NAV : FLAG_LIGHT_NAV;

        setShiftRange(dp.allAppsShiftRange);
        mAllAppScale.value = 1;
        mLauncher.addOnDeviceProfileChangeListener(this);
        mVibratorWrapper = VibratorWrapper.INSTANCE.get(mLauncher.getApplicationContext());
    }

    public float getShiftRange() {
        return mShiftRange;
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mIsVerticalLayout = dp.isVerticalBarLayout();
        setShiftRange(dp.allAppsShiftRange);

        if (mIsVerticalLayout) {
            mLauncher.getHotseat().setTranslationY(0);
            mLauncher.getWorkspace().getPageIndicator().setTranslationY(0);
        }

        mIsTablet = dp.isTablet;
    }

    /**
     * Note this method should not be called outside this class. This is public because it is used
     * in xml-based animations which also handle updating the appropriate UI.
     *
     * @param progress value between 0 and 1, 0 shows all apps and 1 shows workspace
     * @see #setState(LauncherState)
     * @see #setStateWithAnimation(LauncherState, StateAnimationConfig, PendingAnimation)
     */
    public void setProgress(float progress) {
        mProgress = progress;
        boolean fromBackground =
                mLauncher.getStateManager().getCurrentStableState() == BACKGROUND_APP;
        // Allow apps panel to shift the full screen if coming from another app.
        float shiftRange = fromBackground ? mLauncher.getDeviceProfile().heightPx : mShiftRange;
        getAppsViewProgressTranslationY().setValue(mProgress * shiftRange);
        mLauncher.onAllAppsTransition(1 - progress);

        boolean hasScrim = progress < NAV_BAR_COLOR_FORCE_UPDATE_THRESHOLD
                && mLauncher.getAppsView().getNavBarScrimHeight() > 0;
        mLauncher.getSystemUiController().updateUiState(
                UI_STATE_ALL_APPS, hasScrim ? mNavScrimFlag : 0);
    }

    public float getProgress() {
        return mProgress;
    }

    private MultiProperty getAppsViewProgressTranslationY() {
        return mAppsViewTranslationY.get(INDEX_APPS_VIEW_PROGRESS);
    }

    private MultiProperty getAppsViewPullbackTranslationY() {
        return mAppsViewTranslationY.get(INDEX_APPS_VIEW_PULLBACK);
    }

    private MultiProperty getAppsViewProgressAlpha() {
        return mAppsViewAlpha.get(INDEX_APPS_VIEW_PROGRESS);
    }

    private MultiProperty getAppsViewPullbackAlpha() {
        return mAppsViewAlpha.get(INDEX_APPS_VIEW_PULLBACK);
    }

    /**
     * Sets the vertical transition progress to {@param state} and updates all the dependent UI
     * accordingly.
     */
    @Override
    public void setState(LauncherState state) {
        setProgress(state.getVerticalProgress(mLauncher));
        setAlphas(state, new StateAnimationConfig(), NO_ANIM_PROPERTY_SETTER);
    }

    @Override
    public void onBackProgressed(
            LauncherState toState, @FloatRange(from = 0.0, to = 1.0) float backProgress) {
        if (!mLauncher.isInState(ALL_APPS) || !NORMAL.equals(toState)) {
            return;
        }

        float deceleratedProgress =
                Interpolators.PREDICTIVE_BACK_DECELERATED_EASE.getInterpolation(backProgress);
        float scaleProgress = ScrollableLayoutManager.PREDICTIVE_BACK_MIN_SCALE
                + (1 - ScrollableLayoutManager.PREDICTIVE_BACK_MIN_SCALE)
                * (1 - deceleratedProgress);

        mAllAppScale.updateValue(scaleProgress);
    }

    private void onScaleProgressChanged() {
        final float scaleProgress = mAllAppScale.value;
        SCALE_PROPERTY.set(mLauncher.getAppsView(), scaleProgress);
        mLauncher.getScrimView().setScrimHeaderScale(scaleProgress);

        AllAppsRecyclerView rv = mLauncher.getAppsView().getActiveRecyclerView();
        if (rv != null && rv.getScrollbar() != null) {
            rv.getScrollbar().setVisibility(scaleProgress < 1f ? View.INVISIBLE : View.VISIBLE);
        }

        // Disable view clipping from all apps' RecyclerView up to all apps view during scale
        // animation, and vice versa. The goal is to display extra roll(s) app icons (rendered in
        // {@link AppsGridLayoutManager#calculateExtraLayoutSpace}) during scale animation.
        boolean hasScaleEffect = scaleProgress < 1f;
        if (hasScaleEffect != mHasScaleEffect) {
            mHasScaleEffect = hasScaleEffect;
            if (mHasScaleEffect) {
                setClipChildrenOnViewTree(rv, mLauncher.getAppsView(), false);
            } else {
                restoreClipChildrenOnViewTree(rv, mLauncher.getAppsView());
            }
        }
    }

    /** Animate all apps view to 1f scale. */
    public void animateAllAppsToNoScale() {
        mAllAppScale.animateToValue(1f)
                .setDuration(REVERT_SWIPE_ALL_APPS_TO_HOME_ANIMATION_DURATION_MS)
                .start();
    }

    /**
     * Creates an animation which updates the vertical transition progress and updates all the
     * dependent UI using various animation events
     *
     * This method also dictates where along the progress the haptics should be played. As the user
     * scrolls up from workspace or down from AllApps, a drag haptic is being played until the
     * commit point where it plays a commit haptic. Where we play the haptics differs when going
     * from workspace -> allApps and vice versa.
     */
    @Override
    public void setStateWithAnimation(LauncherState toState,
            StateAnimationConfig config, PendingAnimation builder) {
        if (mLauncher.isInState(ALL_APPS) && !ALL_APPS.equals(toState)) {
            builder.addEndListener(success -> {
                // Reset pull back progress and alpha after switching states.
                ALL_APPS_PULL_BACK_TRANSLATION.set(this, ALL_APPS_PULL_BACK_TRANSLATION_DEFAULT);
                ALL_APPS_PULL_BACK_ALPHA.set(this, ALL_APPS_PULL_BACK_ALPHA_DEFAULT);

                mAllAppScale.updateValue(1f);
            });
        }

        if (FeatureFlags.ENABLE_PREMIUM_HAPTICS_ALL_APPS.get() && config.isUserControlled()
                && Utilities.ATLEAST_S) {
            if (toState == ALL_APPS) {
                builder.addOnFrameListener(
                        new VibrationAnimatorUpdateListener(this, mVibratorWrapper,
                                SWIPE_DRAG_COMMIT_THRESHOLD, 1));
            } else {
                builder.addOnFrameListener(
                        new VibrationAnimatorUpdateListener(this, mVibratorWrapper,
                                0, SWIPE_DRAG_COMMIT_THRESHOLD));
            }
            builder.addEndListener((unused) -> {
                mVibratorWrapper.cancelVibrate();
            });
        }

        float targetProgress = toState.getVerticalProgress(mLauncher);
        if (Float.compare(mProgress, targetProgress) == 0) {
            setAlphas(toState, config, builder);
            // Fail fast
            return;
        }

        // need to decide depending on the release velocity
        Interpolator verticalProgressInterpolator = config.getInterpolator(ANIM_VERTICAL_PROGRESS,
                config.isUserControlled() ? LINEAR : DECELERATE_1_7);
        Animator anim = createSpringAnimation(mProgress, targetProgress);
        anim.setInterpolator(verticalProgressInterpolator);
        builder.add(anim);

        setAlphas(toState, config, builder);
        // This controls both haptics for tapping on QSB and going to all apps.
        if (ALL_APPS.equals(toState) && mLauncher.isInState(NORMAL) &&
                !FeatureFlags.ENABLE_PREMIUM_HAPTICS_ALL_APPS.get()) {
            mLauncher.getAppsView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }

    public Animator createSpringAnimation(float... progressValues) {
        return ObjectAnimator.ofFloat(this, ALL_APPS_PROGRESS, progressValues);
    }

    /**
     * Updates the property for the provided state
     */
    public void setAlphas(LauncherState state, StateAnimationConfig config, PropertySetter setter) {
        int visibleElements = state.getVisibleElements(mLauncher);
        boolean hasAllAppsContent = (visibleElements & ALL_APPS_CONTENT) != 0;

        Interpolator allAppsFade = config.getInterpolator(ANIM_ALL_APPS_FADE, LINEAR);
        setter.setFloat(getAppsViewProgressAlpha(), MultiPropertyFactory.MULTI_PROPERTY_VALUE,
                hasAllAppsContent ? 1 : 0, allAppsFade);
        setter.setFloat(getAppsViewPullbackAlpha(), MultiPropertyFactory.MULTI_PROPERTY_VALUE,
                hasAllAppsContent ? 1 : 0, allAppsFade);

        setter.setFloat(mLauncher.getAppsView(),
                ActivityAllAppsContainerView.BOTTOM_SHEET_ALPHA, hasAllAppsContent ? 1 : 0,
                config.getInterpolator(ANIM_ALL_APPS_BOTTOM_SHEET_FADE, INSTANT));

        boolean shouldProtectHeader = !config.hasAnimationFlag(StateAnimationConfig.SKIP_SCRIM)
                && (ALL_APPS == state || mLauncher.getStateManager().getState() == ALL_APPS);
        mScrimView.setDrawingController(shouldProtectHeader ? mAppsView : null);
    }

    /**
     * see Launcher#setupViews
     */
    public void setupViews(ScrimView scrimView, ActivityAllAppsContainerView<Launcher> appsView) {
        mScrimView = scrimView;
        mAppsView = appsView;
        mAppsView.setScrimView(scrimView);

        mAppsViewAlpha = new MultiValueAlpha(mAppsView, APPS_VIEW_INDEX_COUNT,
                FeatureFlags.ALL_APPS_GONE_VISIBILITY.get() ? View.GONE : View.INVISIBLE);
        mAppsViewAlpha.setUpdateVisibility(true);
        mAppsViewTranslationY = new MultiPropertyFactory<>(
                mAppsView, VIEW_TRANSLATE_Y, APPS_VIEW_INDEX_COUNT, Float::sum);
    }

    /**
     * Updates the total scroll range but does not update the UI.
     */
    public void setShiftRange(float shiftRange) {
        mShiftRange = shiftRange;
    }

    /**
     * This VibrationAnimatorUpdateListener class takes in four parameters, a controller, start
     * threshold, end threshold, and a Vibrator wrapper. We use the progress given by the controller
     * as it gives an accurate progress that dictates where the vibrator should vibrate.
     * Note: once the user begins a gesture and does the commit haptic, there should not be anymore
     * haptics played for that gesture.
     */
    private static class VibrationAnimatorUpdateListener implements
            ValueAnimator.AnimatorUpdateListener {
        private final VibratorWrapper mVibratorWrapper;
        private final AllAppsTransitionController mController;
        private final float mStartThreshold;
        private final float mEndThreshold;
        private boolean mHasCommitted;

        VibrationAnimatorUpdateListener(AllAppsTransitionController controller,
                                        VibratorWrapper vibratorWrapper, float startThreshold,
                                        float endThreshold) {
            mController = controller;
            mVibratorWrapper = vibratorWrapper;
            mStartThreshold = startThreshold;
            mEndThreshold = endThreshold;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if (mHasCommitted) {
                return;
            }
            float currentProgress =
                    AllAppsTransitionController.ALL_APPS_PROGRESS.get(mController);
            if (currentProgress > mStartThreshold && currentProgress < mEndThreshold) {
                mVibratorWrapper.vibrateForDragTexture();
            } else if (!(currentProgress == 0 || currentProgress == 1)) {
                // This check guards against committing at the location of the start of the gesture
                mVibratorWrapper.vibrateForDragCommit();
                mHasCommitted = true;
            }
        }
    }
}
