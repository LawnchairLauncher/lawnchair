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

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.taskbar.TaskbarInteractor;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.window.RecentsWindowFlags;
import com.android.quickstep.window.RecentsWindowManager;
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

    /**
     * Closes any overlays.
     */
    public void closeOverlay() {
        Optional.ofNullable(getTaskbarInteractor()).ifPresent(
                TaskbarInteractor::hideOverlayWindow);
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

    @Override
    public boolean isLauncherOverlayShowing() {
        if (!RecentsWindowFlags.enableLauncherOverviewInWindow) {
            return false;
        }
        Launcher launcher = Launcher.ACTIVITY_TRACKER.getCreatedContext();

        return launcher != null && launcher.getWorkspace().isOverlayShown();
    }

    /**
     * todo: Create an abstract animation factory to handle both activity and window implementations
     * todo: move new factory into BaseContainerInterface and cleanup.
      */

    class DefaultAnimationFactory implements AnimationFactory<RecentsState, RecentsWindowManager> {

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
        public RecentsWindowManager getContainer() {
            return mRecentsWindowManager;
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

        protected void createBackgroundToOverviewAnim(RecentsWindowManager container,
                PendingAnimation pa) {
            //  Scale down recents from being full screen to being in overview.
            RecentsView recentsView = container.getOverviewPanel();
            pa.addFloat(recentsView, RECENTS_SCALE_PROPERTY,
                    recentsView.getMaxScaleForFullScreen(), 1, LINEAR);
            pa.addFloat(recentsView, FULLSCREEN_PROGRESS, 1, 0, LINEAR);
        }
    }
}
