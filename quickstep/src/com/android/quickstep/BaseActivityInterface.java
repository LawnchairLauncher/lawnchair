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
import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
import static com.android.quickstep.AbsSwipeUpHandler.RECENTS_ATTACH_DURATION;
import static com.android.quickstep.GestureState.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_FADE_ANIM;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_TRANSLATE_X_ANIM;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.graphics.Color;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
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
    private final STATE_TYPE mBackgroundState;

    private STATE_TYPE mTargetState;

    @Nullable private Runnable mOnInitBackgroundStateUICallback = null;

    protected BaseActivityInterface(boolean rotationSupportedByActivity,
            STATE_TYPE overviewState, STATE_TYPE backgroundState) {
        this.rotationSupportedByActivity = rotationSupportedByActivity;
        mTargetState = overviewState;
        mBackgroundState = backgroundState;
    }

    /**
     * Called when the current gesture transition is cancelled.
     * @param activityVisible Whether the user can see the changes we make here, so try to animate.
     * @param endTarget If the gesture ended before we got cancelled, where we were headed.
     */
    public void onTransitionCancelled(boolean activityVisible,
            @Nullable GestureState.GestureEndTarget endTarget) {
        ACTIVITY_TYPE activity = getCreatedContainer();
        if (activity == null) {
            return;
        }
        STATE_TYPE startState = activity.getStateManager().getRestState();
        if (endTarget != null) {
            // We were on our way to this state when we got canceled, end there instead.
            startState = stateFromGestureEndTarget(endTarget);
            DesktopVisibilityController controller = getDesktopVisibilityController();
            if (controller != null && controller.areDesktopTasksVisible()
                    && endTarget == LAST_TASK) {
                // When we are cancelling the transition and going back to last task, move to
                // rest state instead when desktop tasks are visible.
                // If a fullscreen task is visible, launcher goes to normal state when the
                // activity is stopped. This does not happen when desktop tasks are visible
                // on top of launcher. Force the launcher state to rest state here.
                startState = activity.getStateManager().getRestState();
                // Do not animate the transition
                activityVisible = false;
            }
        }
        activity.getStateManager().goToState(startState, activityVisible);
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

    @UiThread
    @Nullable
    public abstract <T extends RecentsView> T getVisibleRecentsView();

    @UiThread
    public abstract boolean switchToRecentsIfVisible(Animator.AnimatorListener animatorListener);

    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        TaskbarUIController controller = getTaskbarController();
        boolean isEventOverBubbleBarStashHandle =
                controller != null && controller.isEventOverBubbleBarStashHandle(ev);
        return deviceState.isInDeferredGestureRegion(ev) || deviceState.isImeRenderingNavButtons()
                || isTrackpadMultiFingerSwipe(ev) || isEventOverBubbleBarStashHandle;
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


    protected void runOnInitBackgroundStateUI(Runnable callback) {
        ACTIVITY_TYPE activity = getCreatedContainer();
        if (activity != null && activity.getStateManager().getState() == mBackgroundState) {
            callback.run();
            onInitBackgroundStateUI();
            return;
        }
        mOnInitBackgroundStateUICallback = callback;
    }

    private void onInitBackgroundStateUI() {
        if (mOnInitBackgroundStateUICallback != null) {
            mOnInitBackgroundStateUICallback.run();
            mOnInitBackgroundStateUICallback = null;
        }
    }

    public interface AnimationFactory {

        void createActivityInterface(long transitionLength);

        /**
         * @param attached Whether to show RecentsView alongside the app window. If false, recents
         *                 will be hidden by some property we can animate, e.g. alpha.
         * @param animate Whether to animate recents to/from its new attached state.
         */
        default void setRecentsAttachedToAppWindow(boolean attached, boolean animate) { }

        default boolean isRecentsAttachedToAppWindow() {
            return false;
        }

        default boolean hasRecentsEverAttachedToAppWindow() {
            return false;
        }

        /** Called when the gesture ends and we know what state it is going towards */
        default void setEndTarget(GestureState.GestureEndTarget endTarget) { }
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
        public void createActivityInterface(long transitionLength) {
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
                setRecentsAttachedToAppWindow(mIsAttachedToWindow, false);
            }
        }

        @Override
        public void setRecentsAttachedToAppWindow(boolean attached, boolean animate) {
            if (mIsAttachedToWindow == attached && animate) {
                return;
            }
            mActivity.getStateManager()
                    .cancelStateElementAnimation(INDEX_RECENTS_FADE_ANIM);
            mActivity.getStateManager()
                    .cancelStateElementAnimation(INDEX_RECENTS_TRANSLATE_X_ANIM);

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
                    .createStateElementAnimation(INDEX_RECENTS_FADE_ANIM, attached ? 1 : 0);
            fadeAnim.setInterpolator(attached ? INSTANT : ACCELERATE_2);
            fadeAnim.setDuration(animationDuration);
            animatorSet.play(fadeAnim);

            float fromTranslation = ADJACENT_PAGE_HORIZONTAL_OFFSET.get(
                    mActivity.getOverviewPanel());
            float toTranslation = attached ? 0 : 1;

            Animator translationAnimator = mActivity.getStateManager().createStateElementAnimation(
                    INDEX_RECENTS_TRANSLATE_X_ANIM, fromTranslation, toTranslation);
            translationAnimator.setDuration(animationDuration);
            animatorSet.play(translationAnimator);
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
