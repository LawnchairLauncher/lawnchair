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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.Utilities.dpToPx;
import static com.android.launcher3.Utilities.mapBoundToRange;
import static com.android.launcher3.anim.Interpolators.EXAGGERATED_EASE;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.model.data.ItemInfo.NO_MATCHING_ID;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.launcher3.views.FloatingIconView.getFloatingIconView;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Size;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.Hotseat;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.DynamicResource;
import com.android.launcher3.util.ObjectWrapper;
import com.android.launcher3.views.FloatingIconView;
import com.android.launcher3.views.FloatingView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.StaggeredWorkspaceAnim;
import com.android.quickstep.views.FloatingWidgetView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.plugins.ResourceProvider;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.ArrayList;

/**
 * Temporary class to allow easier refactoring
 */
public class LauncherSwipeHandlerV2 extends
        AbsSwipeUpHandler<BaseQuickstepLauncher, RecentsView, LauncherState> {

    public LauncherSwipeHandlerV2(Context context, RecentsAnimationDeviceState deviceState,
            TaskAnimationManager taskAnimationManager, GestureState gestureState, long touchTimeMs,
            boolean continuingLastGesture, InputConsumerController inputConsumer) {
        super(context, deviceState, taskAnimationManager, gestureState, touchTimeMs,
                continuingLastGesture, inputConsumer);
    }


    @Override
    protected HomeAnimationFactory createHomeAnimationFactory(ArrayList<IBinder> launchCookies,
            long duration, boolean isTargetTranslucent, boolean appCanEnterPip,
            RemoteAnimationTargetCompat runningTaskTarget) {
        if (mActivity == null) {
            mStateCallback.addChangeListener(STATE_LAUNCHER_PRESENT | STATE_HANDLER_INVALIDATED,
                    isPresent -> mRecentsView.startHome());
            return new HomeAnimationFactory() {
                @Override
                public AnimatorPlaybackController createActivityAnimationToHome() {
                    return AnimatorPlaybackController.wrap(new AnimatorSet(), duration);
                }
            };
        }

        final View workspaceView = findWorkspaceView(launchCookies,
                mRecentsView.getRunningTaskView());
        boolean canUseWorkspaceView = workspaceView != null && workspaceView.isAttachedToWindow();

        mActivity.getRootView().setForceHideBackArrow(true);
        if (!TaskAnimationManager.ENABLE_SHELL_TRANSITIONS) {
            mActivity.setHintUserWillBeActive();
        }

        if (!canUseWorkspaceView || appCanEnterPip || mIsSwipeForStagedSplit) {
            return new LauncherHomeAnimationFactory();
        }
        if (workspaceView instanceof LauncherAppWidgetHostView) {
            return createWidgetHomeAnimationFactory((LauncherAppWidgetHostView) workspaceView,
                    isTargetTranslucent, runningTaskTarget);
        }
        return createIconHomeAnimationFactory(workspaceView);
    }

    private HomeAnimationFactory createIconHomeAnimationFactory(View workspaceView) {
        RectF iconLocation = new RectF();
        FloatingIconView floatingIconView = getFloatingIconView(mActivity, workspaceView,
                true /* hideOriginal */, iconLocation, false /* isOpening */);

        // We want the window alpha to be 0 once this threshold is met, so that the
        // FolderIconView can be seen morphing into the icon shape.
        float windowAlphaThreshold = 1f - SHAPE_PROGRESS_DURATION;

        return new FloatingViewHomeAnimationFactory(floatingIconView) {

            @Nullable
            @Override
            protected View getViewIgnoredInWorkspaceRevealAnimation() {
                return workspaceView;
            }

            @NonNull
            @Override
            public RectF getWindowTargetRect() {
                return iconLocation;
            }

            @Override
            public void setAnimation(RectFSpringAnim anim) {
                super.setAnimation(anim);
                anim.addAnimatorListener(floatingIconView);
                floatingIconView.setOnTargetChangeListener(anim::onTargetPositionChanged);
                floatingIconView.setFastFinishRunnable(anim::end);
            }

            @Override
            public void update(RectF currentRect, float progress, float radius) {
                super.update(currentRect, progress, radius);
                floatingIconView.update(1f /* alpha */, 255 /* fgAlpha */, currentRect, progress,
                        windowAlphaThreshold, radius, false);
            }
        };
    }

    private HomeAnimationFactory createWidgetHomeAnimationFactory(
            LauncherAppWidgetHostView hostView, boolean isTargetTranslucent,
            RemoteAnimationTargetCompat runningTaskTarget) {
        final float floatingWidgetAlpha = isTargetTranslucent ? 0 : 1;
        RectF backgroundLocation = new RectF();
        Rect crop = new Rect();
        // We can assume there is only one remote target here because staged split never animates
        // into the app icon, only into the homescreen
        mRemoteTargetHandles[0].getTaskViewSimulator().getCurrentCropRect().roundOut(crop);
        Size windowSize = new Size(crop.width(), crop.height());
        int fallbackBackgroundColor =
                FloatingWidgetView.getDefaultBackgroundColor(mContext, runningTaskTarget);
        FloatingWidgetView floatingWidgetView = FloatingWidgetView.getFloatingWidgetView(mActivity,
                hostView, backgroundLocation, windowSize,
                mRemoteTargetHandles[0].getTaskViewSimulator().getCurrentCornerRadius(),
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
            public void update(RectF currentRect, float progress, float radius) {
                super.update(currentRect, progress, radius);
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
    private View findWorkspaceView(ArrayList<IBinder> launchCookies, TaskView runningTaskView) {
        if (mIsSwipingPipToHome) {
            // Disable if swiping to PIP
            return null;
        }
        if (runningTaskView == null || runningTaskView.getTask() == null
                || runningTaskView.getTask().key.getComponent() == null) {
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

        return mActivity.getFirstMatchForAppClose(launchCookieItemId,
                runningTaskView.getTask().key.getComponent().getPackageName(),
                UserHandle.of(runningTaskView.getTask().key.userId),
                false /* supportsAllAppsState */);
    }

    @Override
    protected void finishRecentsControllerToHome(Runnable callback) {
        mRecentsAnimationController.finish(
                true /* toRecents */, callback, true /* sendUserLeaveHint */);
    }

    private class FloatingViewHomeAnimationFactory extends LauncherHomeAnimationFactory {

        private final float mTransY;
        private final FloatingView mFloatingView;
        private ValueAnimator mBounceBackAnimator;

        FloatingViewHomeAnimationFactory(FloatingView floatingView) {
            mFloatingView = floatingView;

            ResourceProvider rp = DynamicResource.provider(mActivity);
            mTransY = dpToPx(rp.getFloat(R.dimen.swipe_up_trans_y_dp));
        }

        @Override
        public boolean shouldPlayAtomicWorkspaceReveal() {
            return false;
        }

        protected void bounceBackToRestingPosition() {
            final float startValue = mTransY;
            final float endValue = 0;
            // Ensures the velocity is always aligned with the direction.
            float pixelPerSecond = Math.abs(mSwipeVelocity) * Math.signum(endValue - mTransY);

            DragLayer dl = mActivity.getDragLayer();
            Workspace workspace = mActivity.getWorkspace();
            Hotseat hotseat = mActivity.getHotseat();

            ResourceProvider rp = DynamicResource.provider(mActivity);
            ValueAnimator springTransY = new SpringAnimationBuilder(dl.getContext())
                    .setStiffness(rp.getFloat(R.dimen.swipe_up_trans_y_stiffness))
                    .setDampingRatio(rp.getFloat(R.dimen.swipe_up_trans_y_damping))
                    .setMinimumVisibleChange(1f)
                    .setStartValue(startValue)
                    .setEndValue(endValue)
                    .setStartVelocity(pixelPerSecond)
                    .build(dl, VIEW_TRANSLATE_Y);
            springTransY.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dl.setTranslationY(0f);
                    dl.setAlpha(1f);
                    SCALE_PROPERTY.set(workspace, 1f);
                    SCALE_PROPERTY.set(hotseat, 1f);
                }
            });

            mBounceBackAnimator = springTransY;
            mBounceBackAnimator.start();
        }

        @Override
        public void onCancel() {
            mFloatingView.fastFinish();
            if (mBounceBackAnimator != null) {
                mBounceBackAnimator.cancel();
            }
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
            return mActivity.getStateManager().createAnimationToNewWorkspace(
                    NORMAL, accuracy, StateAnimationConfig.SKIP_ALL_ANIMATIONS);
        }

        @Override
        public void playAtomicAnimation(float velocity) {
            new StaggeredWorkspaceAnim(mActivity, velocity, true /* animateOverviewScrim */,
                    getViewIgnoredInWorkspaceRevealAnimation())
                    .start();
        }

        @Override
        public boolean supportSwipePipToHome() {
            return true;
        }
    }
}
