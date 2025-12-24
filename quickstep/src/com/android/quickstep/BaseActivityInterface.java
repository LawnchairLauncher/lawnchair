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

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import androidx.annotation.Nullable;

import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarInteractor;
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
        Optional.ofNullable(getTaskbarInteractor()).ifPresent(
                TaskbarInteractor::hideOverlayWindow);
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

    class DefaultAnimationFactory implements AnimationFactory<STATE_TYPE, ACTIVITY_TYPE> {

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
        public ACTIVITY_TYPE getContainer() {
            return mActivity;
        }

        @Override
        public void onAttachedToWindowStateUpdated(boolean isAttachedToWindow) {
            mIsAttachedToWindow = isAttachedToWindow;
            if (isAttachedToWindow) {
                mHasEverAttachedToWindow = true;
            }
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
        }
    }
}
