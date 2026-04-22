/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import static com.android.app.animation.Interpolators.ACCELERATE_2;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.quickstep.AbsSwipeUpHandler.RECENTS_ATTACH_DURATION;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_ATTACHED_ALPHA_ANIM;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_FADE_ANIM;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_TRANSLATE_X_ANIM;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.RUNNING_TASK_ATTACH_ALPHA;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;

import androidx.annotation.Nullable;

import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Utility class which abstracts out the logical differences between Launcher and RecentsActivity.
 */
public abstract class BaseActivityInterface<STATE_TYPE extends BaseState<STATE_TYPE>,
        ACTIVITY_TYPE extends StatefulActivity<STATE_TYPE> & RecentsViewContainer> extends
        BaseContainerInterface<STATE_TYPE, ACTIVITY_TYPE> {

    private STATE_TYPE mTargetState;

    protected BaseActivityInterface(boolean rotationSupportedByActivity,
            STATE_TYPE overviewState, STATE_TYPE backgroundState) {
        super(backgroundState);
        this.rotationSupportedByActivity = rotationSupportedByActivity;
        mTargetState = overviewState;
    }

    @Nullable
    public abstract ACTIVITY_TYPE getCreatedContainer();

    @Nullable
    public DepthController getDepthController() {
        return null;
    }

    public final boolean isResumed() {
        ACTIVITY_TYPE activity = getCreatedContainer();
        return activity != null && activity.hasBeenResumed();
    }

    public final boolean isStarted() {
        ACTIVITY_TYPE activity = getCreatedContainer();
        return activity != null && activity.isStarted();
    }

    /**
     * Closes any overlays.
     */
    public void closeOverlay() {
        Optional.ofNullable(getTaskbarController()).ifPresent(
                TaskbarUIController::hideOverlayWindow);
    }

    public void switchRunningTaskViewToScreenshot(HashMap<Integer, ThumbnailData> thumbnailDatas,
            Runnable runnable) {
        ACTIVITY_TYPE activity = getCreatedContainer();
        if (activity == null) {
            return;
        }
        RecentsView recentsView = activity.getOverviewPanel();
        if (recentsView == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        recentsView.switchToScreenshot(thumbnailDatas, runnable);
    }

    class DefaultAnimationFactory implements AnimationFactory {

        protected final ACTIVITY_TYPE mActivity;
        private final STATE_TYPE mStartState;
        private final Consumer<AnimatorControllerWithResistance> mCallback;

        private boolean mIsAttachedToWindow;
        private boolean mHasEverAttachedToWindow;

        DefaultAnimationFactory(Consumer<AnimatorControllerWithResistance> callback) {
            mCallback = callback;

            mActivity = getCreatedContainer();
            mStartState = mActivity.getStateManager().getState();
        }

        protected ACTIVITY_TYPE initBackgroundStateUI() {
            STATE_TYPE resetState = mStartState;
            if (mStartState.shouldDisableRestore()) {
                resetState = mActivity.getStateManager().getRestState();
            }
            mActivity.getStateManager().setRestState(resetState);
            mActivity.getStateManager().goToState(mBackgroundState, false);
            onInitBackgroundStateUI();
            return mActivity;
        }

        @Override
        public void createContainerInterface(long transitionLength) {
            PendingAnimation pa = new PendingAnimation(transitionLength * 2);
            createBackgroundToOverviewAnim(mActivity, pa);
            AnimatorPlaybackController controller = pa.createPlaybackController();
            mActivity.getStateManager().setCurrentUserControlledAnimation(controller);

            // Since we are changing the start position of the UI, reapply the state, at the end
            controller.setEndAction(() -> mActivity.getStateManager().goToState(
                    controller.getInterpolatedProgress() > 0.5 ? mTargetState : mBackgroundState,
                    /* animated= */ false));

            RecentsView recentsView = mActivity.getOverviewPanel();
            AnimatorControllerWithResistance controllerWithResistance =
                    AnimatorControllerWithResistance.createForRecents(controller, mActivity,
                            recentsView.getPagedViewOrientedState(), mActivity.getDeviceProfile(),
                            recentsView, RECENTS_SCALE_PROPERTY, recentsView,
                            TASK_SECONDARY_TRANSLATION);
            mCallback.accept(controllerWithResistance);

            // Creating the activity controller animation sometimes reapplies the launcher state
            // (because we set the animation as the current state animation), so we reapply the
            // attached state here as well to ensure recents is shown/hidden appropriately.
            if (DisplayController.getNavigationMode(mActivity) == NavigationMode.NO_BUTTON) {
                setRecentsAttachedToAppWindow(
                        mIsAttachedToWindow, false, recentsView.shouldUpdateRunningTaskAlpha());
            }
        }

        @Override
        public void setRecentsAttachedToAppWindow(
                boolean attached, boolean animate, boolean updateRunningTaskAlpha) {
            if (mIsAttachedToWindow == attached && animate) {
                return;
            }
            mActivity.getStateManager()
                    .cancelStateElementAnimation(INDEX_RECENTS_FADE_ANIM);
            mActivity.getStateManager()
                    .cancelStateElementAnimation(INDEX_RECENTS_TRANSLATE_X_ANIM);
            if (updateRunningTaskAlpha) {
                mActivity.getStateManager()
                        .cancelStateElementAnimation(INDEX_RECENTS_ATTACHED_ALPHA_ANIM);
            }

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    mIsAttachedToWindow = attached;
                    if (attached) {
                        mHasEverAttachedToWindow = true;
                    }
                }});

            long animationDuration = animate ? RECENTS_ATTACH_DURATION : 0;
            Animator fadeAnim = mActivity.getStateManager()
                    .createStateElementAnimation(INDEX_RECENTS_FADE_ANIM, attached ? 1f : 0f);
            fadeAnim.setInterpolator(attached ? INSTANT : ACCELERATE_2);
            fadeAnim.setDuration(animationDuration);
            animatorSet.play(fadeAnim);

            float fromTranslation = ADJACENT_PAGE_HORIZONTAL_OFFSET.get(
                    mActivity.getOverviewPanel());
            float toTranslation = attached ? 0f : 1f;
            Animator translationAnimator = mActivity.getStateManager().createStateElementAnimation(
                    INDEX_RECENTS_TRANSLATE_X_ANIM, fromTranslation, toTranslation);
            translationAnimator.setDuration(animationDuration);
            animatorSet.play(translationAnimator);

            if (updateRunningTaskAlpha) {
                float fromAlpha = RUNNING_TASK_ATTACH_ALPHA.get(mActivity.getOverviewPanel());
                float toAlpha = attached ? 1f : 0f;
                Animator runningTaskAttachAlphaAnimator = mActivity.getStateManager()
                        .createStateElementAnimation(
                                INDEX_RECENTS_ATTACHED_ALPHA_ANIM, fromAlpha, toAlpha);
                runningTaskAttachAlphaAnimator.setDuration(animationDuration);
                animatorSet.play(runningTaskAttachAlphaAnimator);
            }
            animatorSet.start();
        }

        @Override
        public boolean isRecentsAttachedToAppWindow() {
            return mIsAttachedToWindow;
        }

        @Override
        public boolean hasRecentsEverAttachedToAppWindow() {
            return mHasEverAttachedToWindow;
        }

        @Override
        public void setEndTarget(GestureState.GestureEndTarget endTarget) {
            mTargetState = stateFromGestureEndTarget(endTarget);
        }

        protected void createBackgroundToOverviewAnim(ACTIVITY_TYPE activity, PendingAnimation pa) {
            //  Scale down recents from being full screen to being in overview.
            RecentsView recentsView = activity.getOverviewPanel();
            pa.addFloat(recentsView, RECENTS_SCALE_PROPERTY,
                    recentsView.getMaxScaleForFullScreen(), 1, LINEAR);
            pa.addFloat(recentsView, FULLSCREEN_PROGRESS, 1, 0, LINEAR);

            pa.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    TaskbarUIController taskbarUIController = getTaskbarController();
                    if (taskbarUIController != null) {
                        taskbarUIController.setSystemGestureInProgress(true);
                    }
                }
            });
        }
    }
}
