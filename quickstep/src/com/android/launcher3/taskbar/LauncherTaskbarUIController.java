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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.states.StateAnimationConfig;


/**
 * A data source which integrates with a Launcher instance
 * TODO: Rename to have Launcher prefix
 */

public class LauncherTaskbarUIController extends TaskbarUIController {

    private final BaseQuickstepLauncher mLauncher;
    private final TaskbarStateHandler mTaskbarStateHandler;
    private final TaskbarAnimationController mTaskbarAnimationController;
    private final TaskbarHotseatController mHotseatController;

    private final TaskbarActivityContext mContext;
    final TaskbarDragLayer mTaskbarDragLayer;
    final TaskbarView mTaskbarView;

    private @Nullable Animator mAnimator;
    private boolean mIsAnimatingToLauncher;

    public LauncherTaskbarUIController(
            BaseQuickstepLauncher launcher, TaskbarActivityContext context) {
        mContext = context;
        mTaskbarDragLayer = context.getDragLayer();
        mTaskbarView = mTaskbarDragLayer.findViewById(R.id.taskbar_view);

        mLauncher = launcher;
        mTaskbarStateHandler = mLauncher.getTaskbarStateHandler();
        mTaskbarAnimationController = new TaskbarAnimationController(mLauncher,
                createTaskbarAnimationControllerCallbacks());
        mHotseatController = new TaskbarHotseatController(
                mLauncher, mTaskbarView::updateHotseatItems);
    }

    @Override
    protected void onCreate() {
        mTaskbarStateHandler.setAnimationController(mTaskbarAnimationController);
        mTaskbarAnimationController.init();
        mHotseatController.init();
        setTaskbarViewVisible(!mLauncher.hasBeenResumed());
        alignRealHotseatWithTaskbar();
        mLauncher.setTaskbarUIController(this);
    }

    @Override
    protected void onDestroy() {
        if (mAnimator != null) {
            // End this first, in case it relies on properties that are about to be cleaned up.
            mAnimator.end();
        }
        mTaskbarStateHandler.setAnimationController(null);
        mTaskbarAnimationController.cleanup();
        mHotseatController.cleanup();
        setTaskbarViewVisible(true);
        mLauncher.getHotseat().setIconsAlpha(1f);
        mLauncher.setTaskbarUIController(null);
    }

    @Override
    protected boolean isTaskbarTouchable() {
        return !mIsAnimatingToLauncher;
    }

    private TaskbarAnimationControllerCallbacks createTaskbarAnimationControllerCallbacks() {
        return new TaskbarAnimationControllerCallbacks() {
            @Override
            public void updateTaskbarBackgroundAlpha(float alpha) {
                mTaskbarDragLayer.setTaskbarBackgroundAlpha(alpha);
            }

            @Override
            public void updateTaskbarVisibilityAlpha(float alpha) {
                mTaskbarView.setAlpha(alpha);
            }

            @Override
            public void updateImeBarVisibilityAlpha(float alpha) {
                mTaskbarDragLayer.updateImeBarVisibilityAlpha(alpha);
            }

            @Override
            public void updateTaskbarScale(float scale) {
                mTaskbarView.setScaleX(scale);
                mTaskbarView.setScaleY(scale);
            }

            @Override
            public void updateTaskbarTranslationY(float translationY) {
                if (translationY < 0) {
                    // Resize to accommodate the max translation we'll reach.
                    mContext.setTaskbarWindowHeight(mContext.getDeviceProfile().taskbarSize
                            + mLauncher.getHotseat().getTaskbarOffsetY());
                } else {
                    mContext.setTaskbarWindowHeight(mContext.getDeviceProfile().taskbarSize);
                }
                mTaskbarView.setTranslationY(translationY);
            }
        };
    }

    /**
     * Should be called from onResume() and onPause(), and animates the Taskbar accordingly.
     */
    public void onLauncherResumedOrPaused(boolean isResumed) {
        long duration = QuickstepTransitionManager.CONTENT_ALPHA_DURATION;
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        if (isResumed) {
            mAnimator = createAnimToLauncher(null, duration);
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
     */
    public Animator createAnimToLauncher(@Nullable LauncherState toState, long duration) {
        PendingAnimation anim = new PendingAnimation(duration);
        anim.add(mTaskbarAnimationController.createAnimToBackgroundAlpha(0, duration));
        if (toState != null) {
            mTaskbarStateHandler.setStateWithAnimation(toState, new StateAnimationConfig(), anim);
        }

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsAnimatingToLauncher = true;
                mTaskbarView.setHolesAllowedInLayout(true);
                mTaskbarView.updateHotseatItemsVisibility();
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
        anim.add(mTaskbarAnimationController.createAnimToBackgroundAlpha(1, duration));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mTaskbarView.updateHotseatItemsVisibility();
                setTaskbarViewVisible(true);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mTaskbarView.setHolesAllowedInLayout(false);
            }
        });
        return anim.buildAnim();
    }

    @Override
    protected void onImeVisible(TaskbarDragLayer containerView, boolean isVisible) {
        mTaskbarAnimationController.animateToVisibilityForIme(isVisible ? 0 : 1);
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
        return mTaskbarView.isDraggingItem();
    }

    /**
     * Pads the Hotseat to line up exactly with Taskbar's copy of the Hotseat.
     */
    @Override
    public void alignRealHotseatWithTaskbar() {
        Rect hotseatBounds = new Rect();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int hotseatHeight = grid.workspacePadding.bottom + grid.taskbarSize;
        int taskbarOffset = mLauncher.getHotseat().getTaskbarOffsetY();
        int hotseatTopDiff = hotseatHeight - grid.taskbarSize - taskbarOffset;
        int hotseatBottomDiff = taskbarOffset;

        RectF hotseatBoundsF = mTaskbarView.getHotseatBounds();
        Utilities.scaleRectFAboutPivot(hotseatBoundsF, getTaskbarScaleOnHome(),
                mTaskbarView.getPivotX(), mTaskbarView.getPivotY());
        hotseatBoundsF.round(hotseatBounds);
        mLauncher.getHotseat().setPadding(hotseatBounds.left,
                hotseatBounds.top + hotseatTopDiff,
                mTaskbarView.getWidth() - hotseatBounds.right,
                mTaskbarView.getHeight() - hotseatBounds.bottom + hotseatBottomDiff);
    }

    /**
     * Returns the ratio of the taskbar icon size on home vs in an app.
     */
    public float getTaskbarScaleOnHome() {
        DeviceProfile inAppDp = mContext.getDeviceProfile();
        DeviceProfile onHomeDp = mLauncher.getDeviceProfile();
        return (float) onHomeDp.cellWidthPx / inAppDp.cellWidthPx;
    }

    void setTaskbarViewVisible(boolean isVisible) {
        mTaskbarView.setIconsVisibility(isVisible);
        mLauncher.getHotseat().setIconsAlpha(isVisible ? 0f : 1f);
    }

    /**
     * Contains methods that TaskbarAnimationController can call to interface with
     * TaskbarController.
     */
    protected interface TaskbarAnimationControllerCallbacks {
        void updateTaskbarBackgroundAlpha(float alpha);
        void updateTaskbarVisibilityAlpha(float alpha);
        void updateImeBarVisibilityAlpha(float alpha);
        void updateTaskbarScale(float scale);
        void updateTaskbarTranslationY(float translationY);
    }
}
