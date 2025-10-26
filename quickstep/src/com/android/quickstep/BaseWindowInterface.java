/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_FADE_ANIM;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_TRANSLATE_X_ANIM;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Temporary utility class in place for differences needed between
 * Recents in Window in Launcher vs Fallback
 */
public abstract class BaseWindowInterface extends
        BaseContainerInterface<RecentsState, RecentsWindowManager> {

    final String TAG = "BaseWindowInterface";
    private RecentsState mTargetState;


    protected BaseWindowInterface(RecentsState overviewState, RecentsState backgroundState) {
        super(backgroundState);
        mTargetState = overviewState;
    }

    @Nullable
    public abstract RecentsWindowManager getCreatedContainer();

    @Nullable
    public DepthController getDepthController() {
        return null;
    }

    public final boolean isResumed() {
        return isStarted();
    }

    public final boolean isStarted() {
        RecentsWindowManager windowManager = getCreatedContainer();
        return windowManager != null && windowManager.isStarted();
    }

    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        TaskbarUIController controller = getTaskbarController();
        boolean isEventOverBubbleBarStashHandle =
                controller != null && controller.isEventOverBubbleBarViews(ev);
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
        RecentsWindowManager windowManager = getCreatedContainer();
        if (windowManager == null) {
            return;
        }
        RecentsView recentsView = windowManager.getOverviewPanel();
        if (recentsView == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        recentsView.switchToScreenshot(thumbnailDatas, runnable);
    }

    /**
     * todo: Create an abstract animation factory to handle both activity and window implementations
     * todo: move new factory into BaseContainerInterface and cleanup.
      */

    class DefaultAnimationFactory implements AnimationFactory {

        protected final RecentsWindowManager mRecentsWindowManager;
        private final RecentsState mStartState;
        private final Consumer<AnimatorControllerWithResistance> mCallback;

        private boolean mIsAttachedToWindow;
        private boolean mHasEverAttachedToWindow;

        DefaultAnimationFactory(Consumer<AnimatorControllerWithResistance> callback) {
            mCallback = callback;

            mRecentsWindowManager = getCreatedContainer();
            mStartState = mRecentsWindowManager.getStateManager().getState();
        }

        protected RecentsWindowManager initBackgroundStateUI() {
            RecentsState resetState = mStartState;
            if (mStartState.shouldDisableRestore()) {
                resetState = mRecentsWindowManager.getStateManager().getRestState();
            }
            mRecentsWindowManager.getStateManager().setRestState(resetState);
            mRecentsWindowManager.getStateManager().goToState(mBackgroundState, false);
            onInitBackgroundStateUI();
            return mRecentsWindowManager;
        }

        @Override
        public void createContainerInterface(long transitionLength) {
            PendingAnimation pa = new PendingAnimation(transitionLength * 2);
            createBackgroundToOverviewAnim(mRecentsWindowManager, pa);
            AnimatorPlaybackController controller = pa.createPlaybackController();
            mRecentsWindowManager.getStateManager().setCurrentUserControlledAnimation(controller);

            // Since we are changing the start position of the UI, reapply the state, at the end
            controller.setEndAction(() -> {
                mRecentsWindowManager.getStateManager().goToState(
                        controller.getInterpolatedProgress() > 0.5 ? mTargetState
                                : mBackgroundState,
                        /* animated= */ false);
            });

            RecentsView recentsView = mRecentsWindowManager.getOverviewPanel();
            AnimatorControllerWithResistance controllerWithResistance =
                    AnimatorControllerWithResistance.createForRecents(controller,
                            mRecentsWindowManager, recentsView.getPagedViewOrientedState(),
                            mRecentsWindowManager.getDeviceProfile(), recentsView,
                            RECENTS_SCALE_PROPERTY, recentsView, TASK_SECONDARY_TRANSLATION);
            mCallback.accept(controllerWithResistance);

            // Creating the activity controller animation sometimes reapplies the launcher state
            // (because we set the animation as the current state animation), so we reapply the
            // attached state here as well to ensure recents is shown/hidden appropriately.
            if (DisplayController.getNavigationMode(mRecentsWindowManager)
                    == NavigationMode.NO_BUTTON) {
                setRecentsAttachedToAppWindow(mIsAttachedToWindow, false, false);
            }
        }

        @Override
        public void setRecentsAttachedToAppWindow(boolean attached, boolean animate,
                boolean updateRunningTaskAlpha) {

            if (mIsAttachedToWindow == attached && animate) {
                return;
            }
            mRecentsWindowManager.getStateManager()
                    .cancelStateElementAnimation(INDEX_RECENTS_FADE_ANIM);
            mRecentsWindowManager.getStateManager()
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
            Animator fadeAnim = mRecentsWindowManager.getStateManager()
                    .createStateElementAnimation(INDEX_RECENTS_FADE_ANIM, attached ? 1 : 0);
            fadeAnim.setInterpolator(attached ? INSTANT : ACCELERATE_2);
            fadeAnim.setDuration(animationDuration);
            animatorSet.play(fadeAnim);

            float fromTranslation = ADJACENT_PAGE_HORIZONTAL_OFFSET.get(
                    mRecentsWindowManager.getOverviewPanel());
            float toTranslation = attached ? 0 : 1;

            Animator translationAnimator =
                    mRecentsWindowManager.getStateManager().createStateElementAnimation(
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

        protected void createBackgroundToOverviewAnim(RecentsWindowManager container,
                PendingAnimation pa) {
            //  Scale down recents from being full screen to being in overview.
            RecentsView recentsView = container.getOverviewPanel();
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