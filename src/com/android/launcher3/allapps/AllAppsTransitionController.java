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

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.ALL_APPS_CONTENT;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.Interpolators.DEACCEL_1_7;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.util.FloatProperty;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiValueAlpha;
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
    private boolean mIsVerticalLayout;

    // Whether this class should take care of closing the keyboard.
    private boolean mShouldControlKeyboard;

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

    public AllAppsTransitionController(Launcher l) {
        mLauncher = l;
        DeviceProfile dp = mLauncher.getDeviceProfile();
        setShiftRange(dp.allAppsShiftRange);
        mProgress = 1f;
        mIsVerticalLayout = dp.isVerticalBarLayout();
        mIsTablet = dp.isTablet;
        mLauncher.addOnDeviceProfileChangeListener(this);
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
        getAppsViewProgressTranslationY().setValue(mProgress * mShiftRange);
        mLauncher.onAllAppsTransition(1 - progress);
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
        onProgressAnimationEnd();
    }

    /**
     * Creates an animation which updates the vertical transition progress and updates all the
     * dependent UI using various animation events
     */
    @Override
    public void setStateWithAnimation(LauncherState toState,
            StateAnimationConfig config, PendingAnimation builder) {
        if (mLauncher.isInState(ALL_APPS) && !ALL_APPS.equals(toState)) {
            // For atomic animations, we close the keyboard immediately.
            if (!config.userControlled && mShouldControlKeyboard) {
                mLauncher.getAppsView().getSearchUiManager().getEditText().hideKeyboard();
            }

            builder.addEndListener(success -> {
                // Reset pull back progress and alpha after switching states.
                ALL_APPS_PULL_BACK_TRANSLATION.set(this, ALL_APPS_PULL_BACK_TRANSLATION_DEFAULT);
                ALL_APPS_PULL_BACK_ALPHA.set(this, ALL_APPS_PULL_BACK_ALPHA_DEFAULT);

                // We only want to close the keyboard if the animation has completed successfully.
                // The reason is that with keyboard sync, if the user swipes down from All Apps with
                // the keyboard open and then changes their mind and swipes back up, we want the
                // keyboard to remain open. However an onCancel signal is sent to the listeners
                // (success = false), so we need to check for that.
                if (config.userControlled && success && mShouldControlKeyboard) {
                    mLauncher.getAppsView().getSearchUiManager().getEditText().hideKeyboard();
                }
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
                config.userControlled ? LINEAR : DEACCEL_1_7);
        Animator anim = createSpringAnimation(mProgress, targetProgress);
        anim.setInterpolator(verticalProgressInterpolator);
        anim.addListener(getProgressAnimatorListener());
        builder.add(anim);

        setAlphas(toState, config, builder);

        if (ALL_APPS.equals(toState) && mLauncher.isInState(NORMAL)) {
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

        boolean shouldProtectHeader =
                ALL_APPS == state || mLauncher.getStateManager().getState() == ALL_APPS;
        mScrimView.setDrawingController(shouldProtectHeader ? mAppsView : null);
    }

    public AnimatorListener getProgressAnimatorListener() {
        return AnimatorListeners.forSuccessCallback(this::onProgressAnimationEnd);
    }

    /**
     * see Launcher#setupViews
     */
    public void setupViews(ScrimView scrimView, ActivityAllAppsContainerView<Launcher> appsView) {
        mScrimView = scrimView;
        mAppsView = appsView;
        mAppsView.setScrimView(scrimView);

        mAppsViewAlpha = new MultiValueAlpha(mAppsView, APPS_VIEW_INDEX_COUNT);
        mAppsViewAlpha.setUpdateVisibility(true);
        mAppsViewTranslationY = new MultiPropertyFactory<>(
                mAppsView, VIEW_TRANSLATE_Y, APPS_VIEW_INDEX_COUNT, Float::sum);

        mShouldControlKeyboard = !mLauncher.getSearchConfig().isKeyboardSyncEnabled();
    }

    /**
     * Updates the total scroll range but does not update the UI.
     */
    public void setShiftRange(float shiftRange) {
        mShiftRange = shiftRange;
    }

    /**
     * Set the final view states based on the progress.
     * TODO: This logic should go in {@link LauncherState}
     */
    private void onProgressAnimationEnd() {
        if (Float.compare(mProgress, 1f) == 0) {
            mAppsView.reset(false /* animate */);
            if (mShouldControlKeyboard) {
                mLauncher.getAppsView().getSearchUiManager().getEditText().hideKeyboard();
            }
        }
    }
}
