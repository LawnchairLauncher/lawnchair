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

import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_HOME;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Rect;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener;
import com.android.quickstep.RecentsAnimationController;
import com.android.systemui.shared.recents.model.ThumbnailData;


/**
 * A data source which integrates with a Launcher instance
 */
public class LauncherTaskbarUIController extends TaskbarUIController {

    private final BaseQuickstepLauncher mLauncher;
    private final TaskbarStateHandler mTaskbarStateHandler;
    private final TaskbarHotseatController mHotseatController;

    private final TaskbarActivityContext mContext;
    final TaskbarDragLayer mTaskbarDragLayer;
    final TaskbarView mTaskbarView;

    private final AnimatedFloat mIconAlignmentForResumedState =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);
    private final AnimatedFloat mIconAlignmentForGestureState =
            new AnimatedFloat(this::onIconAlignmentRatioChanged);

    private AnimatedFloat mTaskbarBackgroundAlpha;
    private AlphaProperty mIconAlphaForHome;
    private boolean mIsAnimatingToLauncher;
    private TaskbarKeyguardController mKeyguardController;

    private LauncherState mTargetStateOverride = null;
    private TaskbarControllers mControllers;

    public LauncherTaskbarUIController(
            BaseQuickstepLauncher launcher, TaskbarActivityContext context) {
        mContext = context;
        mTaskbarDragLayer = context.getDragLayer();
        mTaskbarView = mTaskbarDragLayer.findViewById(R.id.taskbar_view);

        mLauncher = launcher;
        mTaskbarStateHandler = mLauncher.getTaskbarStateHandler();
        mHotseatController = new TaskbarHotseatController(
                mLauncher, mTaskbarView::updateHotseatItems);
    }

    @Override
    protected void init(TaskbarControllers taskbarControllers) {
        mTaskbarBackgroundAlpha = taskbarControllers.taskbarDragLayerController
                .getTaskbarBackgroundAlpha();
        MultiValueAlpha taskbarIconAlpha = taskbarControllers.taskbarViewController
                .getTaskbarIconAlpha();
        mIconAlphaForHome = taskbarIconAlpha.getProperty(ALPHA_INDEX_HOME);
        mControllers = taskbarControllers;

        mHotseatController.init();
        mLauncher.setTaskbarUIController(this);
        mKeyguardController = taskbarControllers.taskbarKeyguardController;
        onLauncherResumedOrPaused(mLauncher.hasBeenResumed());
        mIconAlignmentForResumedState.finishAnimation();
    }

    @Override
    protected void onDestroy() {
        mHotseatController.cleanup();
        setTaskbarViewVisible(true);
        mLauncher.getHotseat().setIconsAlpha(1f);
        mLauncher.setTaskbarUIController(null);
    }

    @Override
    protected boolean isTaskbarTouchable() {
        return !mIsAnimatingToLauncher && mTargetStateOverride == null;
    }

    @Override
    protected void updateContentInsets(Rect outContentInsets) {
        // TaskbarDragLayer provides insets to other apps based on contentInsets. These
        // insets should stay consistent even if we expand TaskbarDragLayer's bounds, e.g.
        // to show a floating view like Folder. Thus, we set the contentInsets to be where
        // mTaskbarView is, since its position never changes and insets rather than overlays.
        outContentInsets.left = mTaskbarView.getLeft();
        outContentInsets.top = mTaskbarView.getTop();
        outContentInsets.right = mTaskbarDragLayer.getWidth() - mTaskbarView.getRight();
        outContentInsets.bottom = mTaskbarDragLayer.getHeight() - mTaskbarView.getBottom();
    }

    /**
     * Should be called from onResume() and onPause(), and animates the Taskbar accordingly.
     */
    public void onLauncherResumedOrPaused(boolean isResumed) {
        if (mKeyguardController.isScreenOff()) {
            if (!isResumed) {
                return;
            } else {
                // Resuming implicitly means device unlocked
                mKeyguardController.setScreenOn();
            }
        }

        ObjectAnimator anim = mIconAlignmentForResumedState.animateToValue(
                getCurrentIconAlignmentRatio(), isResumed ? 1 : 0)
                .setDuration(QuickstepTransitionManager.CONTENT_ALPHA_DURATION);

        anim.addListener(AnimatorListeners.forEndCallback(() -> mIsAnimatingToLauncher = false));
        anim.start();
        mIsAnimatingToLauncher = isResumed;
    }

    /**
     * Create Taskbar animation when going from an app to Launcher as part of recents transition.
     * @param toState If known, the state we will end up in when reaching Launcher.
     * @param callbacks callbacks to track the recents animation lifecycle. The state change is
     *                 automatically reset once the recents animation finishes
     */
    public Animator createAnimToLauncher(@NonNull LauncherState toState,
            @NonNull RecentsAnimationCallbacks callbacks,
            long duration) {
        ObjectAnimator animator = mIconAlignmentForGestureState
                .animateToValue(mIconAlignmentForGestureState.value, 1)
                .setDuration(duration);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mTargetStateOverride = null;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                mTargetStateOverride = toState;
            }
        });
        callbacks.addListener(new RecentsAnimationListener() {
            @Override
            public void onRecentsAnimationCanceled(ThumbnailData thumbnailData) {
                endGestureStateOverride();
            }

            @Override
            public void onRecentsAnimationFinished(RecentsAnimationController controller) {
                endGestureStateOverride();
            }

            private void endGestureStateOverride() {
                callbacks.removeListener(this);
                mIconAlignmentForGestureState
                        .animateToValue(mIconAlignmentForGestureState.value, 0)
                        .start();
            }
        });
        return animator;
    }

    private float getCurrentIconAlignmentRatio() {
        return  Math.max(mIconAlignmentForResumedState.value, mIconAlignmentForGestureState.value);
    }

    private void onIconAlignmentRatioChanged() {
        if (mControllers == null) {
            return;
        }
        float alignment = getCurrentIconAlignmentRatio();
        mControllers.taskbarViewController.setLauncherIconAlignment(
                alignment, mLauncher.getDeviceProfile());

        mTaskbarBackgroundAlpha.updateValue(1 - alignment);

        LauncherState state = mTargetStateOverride != null ? mTargetStateOverride
                : mLauncher.getStateManager().getState();
        if ((state.getVisibleElements(mLauncher) & HOTSEAT_ICONS) != 0) {
            // If the hotseat icons are visible, then switch taskbar in last frame
            setTaskbarViewVisible(alignment < 1);
        } else {
            mLauncher.getHotseat().setIconsAlpha(1);
            mIconAlphaForHome.setValue(1 - alignment);
        }
    }

    /**
     * Should be called when one or more items in the Hotseat have changed.
     */
    public void onHotseatUpdated() {
        mHotseatController.onHotseatUpdated();
    }

    /**
     * @param ev MotionEvent in screen coordinates.
     * @return Whether any Taskbar item could handle the given MotionEvent if given the chance.
     */
    public boolean isEventOverAnyTaskbarItem(MotionEvent ev) {
        return mTaskbarView.isEventOverAnyItem(ev);
    }

    public boolean isDraggingItem() {
        return mContext.getDragController().isDragging();
    }

    void setTaskbarViewVisible(boolean isVisible) {
        mIconAlphaForHome.setValue(isVisible ? 1 : 0);
        mLauncher.getHotseat().setIconsAlpha(isVisible ? 0f : 1f);
    }
}
