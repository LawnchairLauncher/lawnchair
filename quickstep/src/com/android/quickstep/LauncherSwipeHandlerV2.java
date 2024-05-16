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

import static com.android.app.animation.Interpolators.EXAGGERATED_EASE;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.Utilities.mapBoundToRange;
import static com.android.launcher3.model.data.ItemInfo.NO_MATCHING_ID;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.launcher3.views.FloatingIconView.getFloatingIconView;

import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Size;
import android.view.RemoteAnimationTarget;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ObjectWrapper;
import com.android.launcher3.views.ClipIconView;
import com.android.launcher3.views.FloatingIconView;
import com.android.launcher3.views.FloatingView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.ScalingWorkspaceRevealAnim;
import com.android.quickstep.util.StaggeredWorkspaceAnim;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.views.FloatingWidgetView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.InputConsumerController;

import java.util.Collections;
import java.util.List;

/**
 * Temporary class to allow easier refactoring
 */
public class LauncherSwipeHandlerV2 extends
        AbsSwipeUpHandler<QuickstepLauncher, RecentsView, LauncherState> {

    public LauncherSwipeHandlerV2(Context context, RecentsAnimationDeviceState deviceState,
            TaskAnimationManager taskAnimationManager, GestureState gestureState, long touchTimeMs,
            boolean continuingLastGesture, InputConsumerController inputConsumer) {
        super(context, deviceState, taskAnimationManager, gestureState, touchTimeMs,
                continuingLastGesture, inputConsumer);
    }


    @Override
    protected HomeAnimationFactory createHomeAnimationFactory(
            List<IBinder> launchCookies,
            long duration,
            boolean isTargetTranslucent,
            boolean appCanEnterPip,
            RemoteAnimationTarget runningTaskTarget,
            @Nullable TaskView targetTaskView) {
        if (mContainer == null) {
            mStateCallback.addChangeListener(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                    isPresent -> mRecentsView.startHome());
            return new HomeAnimationFactory() {
                @Override
                public AnimatorPlaybackController createActivityAnimationToHome() {
                    return AnimatorPlaybackController.wrap(new AnimatorSet(), duration);
                }
            };
        }

        TaskView sourceTaskView = mRecentsView == null && targetTaskView == null
                ? null
                : targetTaskView == null
                        ? mRecentsView.getRunningTaskView()
                        : targetTaskView;
        final View workspaceView = findWorkspaceView(
                targetTaskView == null ? launchCookies : Collections.emptyList(),
                sourceTaskView);
        boolean canUseWorkspaceView = workspaceView != null
                && workspaceView.isAttachedToWindow()
                && workspaceView.getHeight() > 0
                && (mContainer.getDesktopVisibilityController() == null
                || !mContainer.getDesktopVisibilityController().areDesktopTasksVisible());

        mContainer.getRootView().setForceHideBackArrow(true);
        if (!TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            mContainer.setHintUserWillBeActive();
        }

        if (!canUseWorkspaceView || appCanEnterPip || mIsSwipeForSplit) {
            return new LauncherHomeAnimationFactory() {

                @Nullable
                @Override
                public TaskView getTargetTaskView() {
                    return targetTaskView;
                }
            };
        }
        if (workspaceView instanceof LauncherAppWidgetHostView) {
            return createWidgetHomeAnimationFactory((LauncherAppWidgetHostView) workspaceView,
                    isTargetTranslucent, runningTaskTarget);
        }
        return createIconHomeAnimationFactory(workspaceView, targetTaskView);
    }

    private HomeAnimationFactory createIconHomeAnimationFactory(
            View workspaceView, @Nullable TaskView targetTaskView) {
        RectF iconLocation = new RectF();
        FloatingIconView floatingIconView = getFloatingIconView(mContainer, workspaceView, null,
                mContainer.getTaskbarUIController() == null
                        ? null
                        : mContainer.getTaskbarUIController().findMatchingView(workspaceView),
                true /* hideOriginal */, iconLocation, false /* isOpening */);

        // We want the window alpha to be 0 once this threshold is met, so that the
        // FolderIconView can be seen morphing into the icon shape.
        float windowAlphaThreshold = 1f - SHAPE_PROGRESS_DURATION;

        return new FloatingViewHomeAnimationFactory(floatingIconView) {
            @Nullable
            private RectF mTargetRect;
            @Nullable
            private RectFSpringAnim mSiblingAnimation;

            @Nullable
            @Override
            protected View getViewIgnoredInWorkspaceRevealAnimation() {
                return workspaceView;
            }

            @Override
            public boolean isInHotseat() {
                return workspaceView.getTag() instanceof ItemInfo
                        && ((ItemInfo) workspaceView.getTag()).isInHotseat();
            }

            @NonNull
            @Override
            public RectF getWindowTargetRect() {
                if (enableScalingRevealHomeAnimation()) {
                    if (mTargetRect == null) {
                        mTargetRect = new RectF(iconLocation);
                    }
                    return mTargetRect;
                } else {
                    return iconLocation;
                }
            }

            @Override
            public void playAtomicAnimation(float velocity) {
                if (enableScalingRevealHomeAnimation()) {
                    if (mContainer != null) {
                        new ScalingWorkspaceRevealAnim(
                                mContainer, mSiblingAnimation, getWindowTargetRect()).start();
                    }
                } else {
                    super.playAtomicAnimation(velocity);
                }
            }

            @Override
            public void setAnimation(RectFSpringAnim anim) {
                super.setAnimation(anim);
                mSiblingAnimation = anim;
                mSiblingAnimation.addAnimatorListener(floatingIconView);
                floatingIconView.setOnTargetChangeListener(
                        mSiblingAnimation::onTargetPositionChanged);
                floatingIconView.setFastFinishRunnable(mSiblingAnimation::end);
            }

            @Override
            public void update(
                    RectF currentRect,
                    float progress,
                    float radius,
                    int overlayAlpha) {
                floatingIconView.update(1f /* alpha */, currentRect, progress, windowAlphaThreshold,
                        radius, false, overlayAlpha);
            }

            @Override
            public boolean isAnimationReady() {
                return floatingIconView.isLaidOut();
            }

            @Override
            public void setTaskViewArtist(ClipIconView.TaskViewArtist taskViewArtist) {
                super.setTaskViewArtist(taskViewArtist);
                floatingIconView.setOverlayArtist(taskViewArtist);
            }

            @Override
            public boolean isAnimatingIntoIcon() {
                return true;
            }

            @Nullable
            @Override
            public TaskView getTargetTaskView() {
                return targetTaskView;
            }
        };
    }

    private HomeAnimationFactory createWidgetHomeAnimationFactory(
            LauncherAppWidgetHostView hostView, boolean isTargetTranslucent,
            RemoteAnimationTarget runningTaskTarget) {
        final float floatingWidgetAlpha = isTargetTranslucent ? 0 : 1;
        RectF backgroundLocation = new RectF();
        Rect crop = new Rect();
        // We can assume there is only one remote target here because staged split never animates
        // into the app icon, only into the homescreen
        RemoteTargetGluer.RemoteTargetHandle remoteTargetHandle = mRemoteTargetHandles[0];
        TaskViewSimulator tvs = remoteTargetHandle.getTaskViewSimulator();
        // This is to set up the inverse matrix in the simulator
        tvs.apply(remoteTargetHandle.getTransformParams());
        tvs.getCurrentCropRect().roundOut(crop);
        Size windowSize = new Size(crop.width(), crop.height());
        int fallbackBackgroundColor =
                FloatingWidgetView.getDefaultBackgroundColor(mContext, runningTaskTarget);
        FloatingWidgetView floatingWidgetView = FloatingWidgetView.getFloatingWidgetView(mContainer,
                hostView, backgroundLocation, windowSize, tvs.getCurrentCornerRadius(),
                isTargetTranslucent, fallbackBackgroundColor);

        return new FloatingViewHomeAnimationFactory(floatingWidgetView) {

            @Override
            @Nullable
            protected View getViewIgnoredInWorkspaceRevealAnimation() {
                return hostView;
            }

            @Override
            public RectF getWindowTargetRect() {
                super.getWindowTargetRect();
                return backgroundLocation;
            }

            @Override
            public float getEndRadius(RectF cropRectF) {
                return floatingWidgetView.getInitialCornerRadius();
            }

            @Override
            public void setAnimation(RectFSpringAnim anim) {
                super.setAnimation(anim);

                anim.addAnimatorListener(floatingWidgetView);
                floatingWidgetView.setOnTargetChangeListener(anim::onTargetPositionChanged);
                floatingWidgetView.setFastFinishRunnable(anim::end);
            }

            @Override
            public void update(RectF currentRect, float progress, float radius, int overlayAlpha) {
                super.update(currentRect, progress, radius, overlayAlpha);
                final float fallbackBackgroundAlpha =
                        1 - mapBoundToRange(progress, 0.8f, 1, 0, 1, EXAGGERATED_EASE);
                final float foregroundAlpha =
                        mapBoundToRange(progress, 0.5f, 1, 0, 1, EXAGGERATED_EASE);
                floatingWidgetView.update(currentRect, floatingWidgetAlpha, foregroundAlpha,
                        fallbackBackgroundAlpha, 1 - progress);
            }

            @Override
            protected float getWindowAlpha(float progress) {
                return 1 - mapBoundToRange(progress, 0, 0.5f, 0, 1, LINEAR);
            }
        };
    }

    /**
     * Returns the associated view on the workspace matching one of the launch cookies, or the app
     * associated with the running task.
     */
    @Nullable
    private View findWorkspaceView(List<IBinder> launchCookies, TaskView sourceTaskView) {
        if (mIsSwipingPipToHome) {
            // Disable if swiping to PIP
            return null;
        }
        if (sourceTaskView == null || sourceTaskView.getFirstTask().key.getComponent() == null) {
            // Disable if it's an invalid task
            return null;
        }

        // Find the associated item info for the launch cookie (if available), note that predicted
        // apps actually have an id of -1, so use another default id here
        int launchCookieItemId = NO_MATCHING_ID;
        for (IBinder cookie : launchCookies) {
            Integer itemId = ObjectWrapper.unwrap(cookie);
            if (itemId != null) {
                launchCookieItemId = itemId;
                break;
            }
        }

        return mContainer.getFirstMatchForAppClose(launchCookieItemId,
                sourceTaskView.getFirstTask().key.getComponent().getPackageName(),
                UserHandle.of(sourceTaskView.getFirstTask().key.userId),
                false /* supportsAllAppsState */);
    }

    @Override
    protected void finishRecentsControllerToHome(Runnable callback) {
        mRecentsView.cleanupRemoteTargets();
        mRecentsAnimationController.finish(
                true /* toRecents */, callback, true /* sendUserLeaveHint */);
    }

    private class FloatingViewHomeAnimationFactory extends LauncherHomeAnimationFactory {

        private final FloatingView mFloatingView;

        FloatingViewHomeAnimationFactory(FloatingView floatingView) {
            mFloatingView = floatingView;
        }

        @Override
        public void onCancel() {
            mFloatingView.fastFinish();
        }
    }

    private class LauncherHomeAnimationFactory extends HomeAnimationFactory {

        /**
         * Returns a view which should be excluded from the Workspace animation, or null if there
         * is no view to exclude.
         */
        @Nullable
        protected View getViewIgnoredInWorkspaceRevealAnimation() {
            return null;
        }

        @NonNull
        @Override
        public AnimatorPlaybackController createActivityAnimationToHome() {
            // Return an empty APC here since we have an non-user controlled animation
            // to home.
            long accuracy = 2 * Math.max(mDp.widthPx, mDp.heightPx);
            return mContainer.getStateManager().createAnimationToNewWorkspace(
                    NORMAL, accuracy, StateAnimationConfig.SKIP_ALL_ANIMATIONS);
        }

        @Override
        public void playAtomicAnimation(float velocity) {
            new StaggeredWorkspaceAnim(mContainer, velocity, true /* animateOverviewScrim */,
                    getViewIgnoredInWorkspaceRevealAnimation())
                    .start();
        }
    }
}
