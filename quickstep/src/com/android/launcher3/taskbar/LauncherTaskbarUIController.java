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

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_HOME;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_LAUNCHER_STATE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Rect;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.quickstep.AnimatedFloat;


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

    private AnimatedFloat mTaskbarBackgroundAlpha;
    private AlphaProperty mIconAlphaForHome;
    private @Nullable Animator mAnimator;
    private boolean mIsAnimatingToLauncher;
    private TaskbarKeyguardController mKeyguardController;

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
        mTaskbarStateHandler.setAnimationController(taskbarIconAlpha.getProperty(
                ALPHA_INDEX_LAUNCHER_STATE));
        mHotseatController.init();
        setTaskbarViewVisible(!mLauncher.hasBeenResumed());
        mLauncher.setTaskbarUIController(this);
        mKeyguardController = taskbarControllers.taskbarKeyguardController;
    }

    @Override
    protected void onDestroy() {
        if (mAnimator != null) {
            // End this first, in case it relies on properties that are about to be cleaned up.
            mAnimator.end();
        }
        mTaskbarStateHandler.setAnimationController(null);
        mHotseatController.cleanup();
        setTaskbarViewVisible(true);
        mLauncher.getHotseat().setIconsAlpha(1f);
        mLauncher.setTaskbarUIController(null);
    }

    @Override
    protected boolean isTaskbarTouchable() {
        return !mIsAnimatingToLauncher;
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

        long duration = QuickstepTransitionManager.CONTENT_ALPHA_DURATION;
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        if (isResumed) {
            mAnimator = createAnimToLauncher(mLauncher.getStateManager().getState(), duration);
        } else {
            mAnimator = createAnimToApp(duration);
        }
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimator = null;
            }
        });
        mAnimator.start();
    }

    /**
     * Create Taskbar animation when going from an app to Launcher.
     * @param toState If known, the state we will end up in when reaching Launcher.
     * TODO: Move this and createAnimToApp to TaskbarStateHandler using the BACKGROUND state
     */
    public Animator createAnimToLauncher(@NonNull LauncherState toState, long duration) {
        PendingAnimation anim = new PendingAnimation(duration);
        mTaskbarStateHandler.setState(toState, anim);

        anim.setFloat(mTaskbarBackgroundAlpha, AnimatedFloat.VALUE, 0, LINEAR);
        mTaskbarView.alignIconsWithLauncher(mLauncher.getDeviceProfile(), anim);

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsAnimatingToLauncher = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimatingToLauncher = false;
                setTaskbarViewVisible(false);
            }
        });

        return anim.buildAnim();
    }

    private Animator createAnimToApp(long duration) {
        PendingAnimation anim = new PendingAnimation(duration);
        anim.setFloat(mTaskbarBackgroundAlpha, AnimatedFloat.VALUE, 1, LINEAR);
        anim.addListener(AnimatorListeners.forEndCallback(mTaskbarView.resetIconPosition(anim)));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setTaskbarViewVisible(true);
            }
        });
        return anim.buildAnim();
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
