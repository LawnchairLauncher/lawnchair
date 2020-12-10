/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;

import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.RectF;
import android.os.UserHandle;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.StaggeredWorkspaceAnim;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.InputConsumerController;

/**
 * Temporary class to allow easier refactoring
 */
public class LauncherSwipeHandlerV2 extends
        BaseSwipeUpHandlerV2<BaseQuickstepLauncher, RecentsView> {

    public LauncherSwipeHandlerV2(Context context, RecentsAnimationDeviceState deviceState,
            TaskAnimationManager taskAnimationManager, GestureState gestureState, long touchTimeMs,
            boolean continuingLastGesture, InputConsumerController inputConsumer) {
        super(context, deviceState, taskAnimationManager, gestureState, touchTimeMs,
                continuingLastGesture, inputConsumer);
    }


    @Override
    protected HomeAnimationFactory createHomeAnimationFactory(long duration) {
        HomeAnimationFactory homeAnimFactory;
        if (mActivity != null) {
            final TaskView runningTaskView = mRecentsView.getRunningTaskView();
            final View workspaceView;
            if (runningTaskView != null
                    && runningTaskView.getTask().key.getComponent() != null) {
                workspaceView = mActivity.getWorkspace().getFirstMatchForAppClose(
                        runningTaskView.getTask().key.getComponent().getPackageName(),
                        UserHandle.of(runningTaskView.getTask().key.userId));
            } else {
                workspaceView = null;
            }
            final RectF iconLocation = new RectF();
            boolean canUseWorkspaceView =
                    workspaceView != null && workspaceView.isAttachedToWindow();
            FloatingIconView floatingIconView = canUseWorkspaceView
                    ? FloatingIconView.getFloatingIconView(mActivity, workspaceView,
                    true /* hideOriginal */, iconLocation, false /* isOpening */)
                    : null;

            mActivity.getRootView().setForceHideBackArrow(true);
            mActivity.setHintUserWillBeActive();

            if (canUseWorkspaceView) {
                // We want the window alpha to be 0 once this threshold is met, so that the
                // FolderIconView can be seen morphing into the icon shape.
                float windowAlphaThreshold = 1f - SHAPE_PROGRESS_DURATION;
                homeAnimFactory = new LauncherHomeAnimationFactory() {
                    @Override
                    public RectF getWindowTargetRect() {
                        return iconLocation;
                    }

                    @Override
                    public void setAnimation(RectFSpringAnim anim) {
                        anim.addAnimatorListener(floatingIconView);
                        floatingIconView.setOnTargetChangeListener(anim::onTargetPositionChanged);
                        floatingIconView.setFastFinishRunnable(anim::end);
                    }

                    @Override
                    public void update(RectF currentRect, float progress, float radius) {
                        floatingIconView.update(currentRect, 1f, progress, windowAlphaThreshold,
                                radius, false);
                    }

                    @Override
                    public void onCancel() {
                        floatingIconView.fastFinish();
                    }
                };
            } else {
                homeAnimFactory = new LauncherHomeAnimationFactory();
            }
        } else {
            homeAnimFactory = new HomeAnimationFactory() {
                @Override
                public AnimatorPlaybackController createActivityAnimationToHome() {
                    return AnimatorPlaybackController.wrap(new AnimatorSet(), duration);
                }
            };
            mStateCallback.addChangeListener(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                    isPresent -> mRecentsView.startHome());
        }
        return homeAnimFactory;
    }

    @Override
    protected void finishRecentsControllerToHome(Runnable callback) {
        mRecentsAnimationController.finish(
                true /* toRecents */, callback, true /* sendUserLeaveHint */);
    }

    private class LauncherHomeAnimationFactory extends HomeAnimationFactory {
        @NonNull
        @Override
        public AnimatorPlaybackController createActivityAnimationToHome() {
            // Return an empty APC here since we have an non-user controlled animation
            // to home.
            long accuracy = 2 * Math.max(mDp.widthPx, mDp.heightPx);
            return mActivity.getStateManager().createAnimationToNewWorkspace(
                    NORMAL, accuracy, 0 /* animComponents */);
        }

        @Override
        public void playAtomicAnimation(float velocity) {
            new StaggeredWorkspaceAnim(mActivity, velocity,
                    true /* animateOverviewScrim */).start();
        }
    }
}
