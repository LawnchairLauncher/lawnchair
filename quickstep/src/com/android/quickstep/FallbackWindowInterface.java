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

import static com.android.launcher3.util.NavigationMode.NO_BUTTON;
import static com.android.quickstep.fallback.RecentsState.BACKGROUND_APP;
import static com.android.quickstep.fallback.RecentsState.DEFAULT;
import static com.android.quickstep.fallback.RecentsState.HOME;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Rect;
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;

import com.android.app.displaylib.PerDisplayRepository;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.views.ScrimColors;
import com.android.quickstep.GestureState.GestureEndTarget;
import com.android.quickstep.dagger.QuickstepBaseAppComponent;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.ContextInitListener;
import com.android.quickstep.views.RecentsView;

import dagger.assisted.AssistedInject;

import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * {@link BaseWindowInterface} for recents when the default launcher is different than the
 * currently running one and apps should interact with the {@link RecentsWindowManager} as opposed
 * to the in-launcher one.
 */
public final class FallbackWindowInterface extends BaseWindowInterface {

    public static final DaggerSingletonObject<PerDisplayRepository<FallbackWindowInterface>>
            REPOSITORY_INSTANCE = new DaggerSingletonObject<>(
            QuickstepBaseAppComponent::getFallbackWindowInterfaceRepository);

    @Nullable private RecentsWindowManager mRecentsWindowManager = null;

    @AssistedInject
    public FallbackWindowInterface() {
        super(DEFAULT, BACKGROUND_APP);
    }

    public void setRecentsWindowManager(@Nullable RecentsWindowManager recentsWindowManager) {
        mRecentsWindowManager = recentsWindowManager;
    }

    /** 2 */
    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect,
            RecentsPagedOrientationHandler orientationHandler) {
        calculateTaskSize(context, dp, outRect, orientationHandler);
        if (dp.isVerticalBarLayout() && DisplayController.getNavigationMode(context) != NO_BUTTON) {
            return dp.isSeascape() ? outRect.left : (dp.getDeviceProperties().getWidthPx() - outRect.right);
        } else {
            return dp.getDeviceProperties().getHeightPx() - outRect.bottom;
        }
    }

    /** 5 */
    @Override
    public void onAssistantVisibilityChanged(float visibility) {
        // This class becomes active when the screen is locked.
        // Rather than having it handle assistant visibility changes, the assistant visibility is
        // set to zero prior to this class becoming active.
    }

    /** 6 */
    @Override
    public BaseWindowInterface.AnimationFactory prepareRecentsUI(boolean activityVisible,
            Consumer<AnimatorControllerWithResistance> callback) {
        notifyRecentsOfOrientation();
        BaseWindowInterface.DefaultAnimationFactory factory =
                new BaseWindowInterface.DefaultAnimationFactory(callback);
        factory.initBackgroundStateUI();
        return factory;
    }

    @Override
    public ContextInitListener<RecentsWindowManager> createActivityInitListener(
            Predicate<Boolean> onInitListener) {
        return new ContextInitListener<>(
                (activity, alreadyOnHome) -> onInitListener.test(alreadyOnHome),
                RecentsWindowManager.getRecentsWindowTracker());
    }

    @Nullable
    @Override
    public RecentsWindowManager getCreatedContainer() {
        return mRecentsWindowManager;
    }

    @Override
    public TaskbarUIController getTaskbarController() {
        RecentsWindowManager manager = getCreatedContainer();
        return manager == null ? null : manager.getTaskbarUIController();
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTarget target) {
        // TODO: Remove this once b/77875376 is fixed
        return target.screenSpaceBounds;
    }

    @Nullable
    @Override
    public <T extends RecentsView<?, ?>> T getVisibleRecentsView() {
        RecentsWindowManager manager = getCreatedContainer();
        if (manager != null && (manager.isStarted() || isInLiveTileMode())) {
            return manager.getOverviewPanel();
        }
        return null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Animator.AnimatorListener animatorListener) {
        return false;
    }

    @Override
    protected ScrimColors getOverviewScrimColorForState(RecentsWindowManager container,
            RecentsState state) {
        return state.getScrimColor(container.asContext());
    }

    @Override
    public void onExitOverview(Runnable exitRunnable) {
        RecentsWindowManager windowManager = getCreatedContainer();
        final StateManager<RecentsState, RecentsWindowManager> stateManager =
                windowManager != null ? windowManager.getStateManager() : null;
        if (stateManager == null || stateManager.getState() == HOME) {
            exitRunnable.run();
            notifyRecentsOfOrientation();
            return;
        }

        stateManager.addStateListener(
                new StateManager.StateListener<RecentsState>() {
                    @Override
                    public void onStateTransitionComplete(RecentsState toState) {
                        // Are we going from Recents to Workspace?
                        if (toState == HOME) {
                            exitRunnable.run();
                            notifyRecentsOfOrientation();
                            stateManager.removeStateListener(this);
                        }
                    }
                });
    }

    @Override
    public boolean isInLiveTileMode() {
        RecentsWindowManager windowManager = getCreatedContainer();
        return windowManager != null && windowManager.getStateManager().getState() == DEFAULT &&
                windowManager.isStarted();
    }

    @Override
    public void onLaunchTaskFailed() {
        // TODO: probably go back to overview instead.
        RecentsWindowManager manager = getCreatedContainer();
        if (manager == null) {
            return;
        }
        manager.<RecentsView>getOverviewPanel().startHome();
    }

    @Override
    public RecentsState stateFromGestureEndTarget(GestureEndTarget endTarget) {
        switch (endTarget) {
            case RECENTS:
                return DEFAULT;
            case NEW_TASK:
            case LAST_TASK:
                return BACKGROUND_APP;
            case HOME:
            case ALL_APPS:
            default:
                return HOME;
        }
    }

    private void notifyRecentsOfOrientation() {
        RecentsWindowManager recentsWindowManager = getCreatedContainer();
        if (recentsWindowManager != null) {
            // reset layout on swipe to home
            ((RecentsView) recentsWindowManager.getOverviewPanel()).reapplyActiveRotation();
        }
    }

    @Override
    public @Nullable Animator getParallelAnimationToGestureEndTarget(GestureEndTarget endTarget,
            long duration, RecentsAnimationCallbacks callbacks) {
        TaskbarUIController uiController = getTaskbarController();
        Animator superAnimator = super.getParallelAnimationToGestureEndTarget(
                endTarget, duration, callbacks);
        if (uiController == null) {
            return superAnimator;
        }
        Animator taskbarAnimator = uiController.getParallelAnimationToGestureEndTarget(
                endTarget, duration, callbacks);
        if (taskbarAnimator == null) {
            return superAnimator;
        }
        if (superAnimator == null) {
            return taskbarAnimator;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(superAnimator, taskbarAnimator);
        return animatorSet;
    }
}
