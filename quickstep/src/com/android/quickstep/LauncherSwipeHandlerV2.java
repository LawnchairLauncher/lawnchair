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
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.PROTOTYPE_APP_CLOSE;
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
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.quickstep.util.AppCloseConfig;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.StaggeredWorkspaceAnim;
import com.android.quickstep.views.FloatingWidgetView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.plugins.ResourceProvider;
import com.android.systemui.shared.system.InputConsumerController;

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
            long duration, boolean isTargetTranslucent) {
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
        mActivity.setHintUserWillBeActive();

        if (!canUseWorkspaceView) {
            return new LauncherHomeAnimationFactory();
        }
        if (workspaceView instanceof LauncherAppWidgetHostView) {
            return createWidgetHomeAnimationFactory((LauncherAppWidgetHostView) workspaceView,
                    isTargetTranslucent);
        }
        return createIconHomeAnimationFactory(workspaceView);
    }

    private HomeAnimationFactory createIconHomeAnimationFactory(View workspaceView) {
        final ResourceProvider rp = DynamicResource.provider(mActivity);
        final float transY = dpToPx(rp.getFloat(R.dimen.swipe_up_trans_y_dp));
        float dpPerSecond = dpToPx(rp.getFloat(R.dimen.swipe_up_trans_y_dp_per_s));
        final float launcherAlphaMax =
                rp.getFloat(R.dimen.swipe_up_launcher_alpha_max_progress);

        RectF iconLocation = new RectF();
        FloatingIconView floatingIconView = getFloatingIconView(mActivity, workspaceView,
                true /* hideOriginal */, iconLocation, false /* isOpening */);

        // We want the window alpha to be 0 once this threshold is met, so that the
        // FolderIconView can be seen morphing into the icon shape.
        float windowAlphaThreshold = 1f - SHAPE_PROGRESS_DURATION;
        return new LauncherHomeAnimationFactory() {

            // There is a delay in loading the icon, so we need to keep the window
            // opaque until it is ready.
            private boolean mIsFloatingIconReady = false;

            private @Nullable ValueAnimator mBounceBackAnimator;

            @Override
            public RectF getWindowTargetRect() {
                if (PROTOTYPE_APP_CLOSE.get()) {
                    // We want the target rect to be at this offset position, so that all
                    // launcher content can spring back upwards.
                    floatingIconView.setPositionOffsetY(transY);
                }
                return iconLocation;
            }

            @Override
            public void setAnimation(RectFSpringAnim anim) {
                anim.addAnimatorListener(floatingIconView);
                floatingIconView.setOnTargetChangeListener(anim::onTargetPositionChanged);
                floatingIconView.setFastFinishRunnable(anim::end);
                if (PROTOTYPE_APP_CLOSE.get()) {
                    mBounceBackAnimator = bounceBackToRestingPosition();
                    // Use a spring to put drag layer translation back to 0.
                    anim.addAnimatorListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            floatingIconView.setPositionOffsetY(0);
                            mBounceBackAnimator.start();
                        }
                    });

                    Workspace workspace = mActivity.getWorkspace();
                    workspace.setPivotToScaleWithSelf(mActivity.getHotseat());
                }
            }

            private ValueAnimator bounceBackToRestingPosition() {
                DragLayer dl = mActivity.getDragLayer();
                Workspace workspace = mActivity.getWorkspace();
                Hotseat hotseat = mActivity.getHotseat();

                final float startValue = transY;
                final float endValue = 0;
                // Ensures the velocity is always aligned with the direction.
                float pixelPerSecond = Math.abs(dpPerSecond) * Math.signum(endValue - transY);

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
                return springTransY;
            }

            @Override
            public boolean keepWindowOpaque() {
                if (mIsFloatingIconReady || floatingIconView.isVisibleToUser()) {
                    mIsFloatingIconReady = true;
                    return false;
                }
                return true;
            }

            @Override
            public void update(@Nullable AppCloseConfig config, RectF currentRect,
                    float progress, float radius) {
                int fgAlpha = 255;
                if (config != null && PROTOTYPE_APP_CLOSE.get()) {
                    DragLayer dl = mActivity.getDragLayer();
                    float translationY = config.getWorkspaceTransY();
                    dl.setTranslationY(translationY);

                    float alpha = mapToRange(progress, 0, launcherAlphaMax, 0, 1f, LINEAR);
                    dl.setAlpha(Math.min(alpha, 1f));

                    float scale = Math.min(1f, config.getWorkspaceScale());
                    SCALE_PROPERTY.set(mActivity.getWorkspace(), scale);
                    SCALE_PROPERTY.set(mActivity.getHotseat(), scale);
                    SCALE_PROPERTY.set(mActivity.getAppsView(), scale);

                    progress = config.getInterpolatedProgress();
                    fgAlpha = config.getFgAlpha();
                }
                floatingIconView.update(1f, fgAlpha, currentRect, progress,
                        windowAlphaThreshold, radius, false);
            }

            @Override
            public void onCancel() {
                floatingIconView.fastFinish();
                if (mBounceBackAnimator != null) {
                    mBounceBackAnimator.cancel();
                }
            }
        };
    }

    private HomeAnimationFactory createWidgetHomeAnimationFactory(
            LauncherAppWidgetHostView hostView, boolean isTargetTranslucent) {

        RectF backgroundLocation = new RectF();
        Rect crop = new Rect();
        mTaskViewSimulator.getCurrentCropRect().roundOut(crop);
        Size windowSize = new Size(crop.width(), crop.height());
        FloatingWidgetView floatingWidgetView = FloatingWidgetView.getFloatingWidgetView(mActivity,
                hostView, backgroundLocation, windowSize,
                mTaskViewSimulator.getCurrentCornerRadius(), isTargetTranslucent);

        return new LauncherHomeAnimationFactory() {

            @Override
            public RectF getWindowTargetRect() {
                return backgroundLocation;
            }

            @Override
            public float getEndRadius(RectF cropRectF) {
                return floatingWidgetView.getInitialCornerRadius();
            }

            @Override
            public void setAnimation(RectFSpringAnim anim) {
                anim.addAnimatorListener(floatingWidgetView);
                floatingWidgetView.setOnTargetChangeListener(anim::onTargetPositionChanged);
                floatingWidgetView.setFastFinishRunnable(anim::end);
            }

            @Override
            public boolean keepWindowOpaque() {
                return false;
            }

            @Override
            public void update(@Nullable AppCloseConfig config, RectF currentRect,
                    float progress, float radius) {
                floatingWidgetView.update(currentRect, 1 /* floatingWidgetAlpha */,
                        config != null ? config.getFgAlpha() : 1f /* foregroundAlpha */,
                        0 /* fallbackBackgroundAlpha */, 1 - progress);
            }

            @Override
            public void onCancel() {
                floatingWidgetView.fastFinish();
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
        int launchCookieItemId = -2;
        for (IBinder cookie : launchCookies) {
            Integer itemId = ObjectWrapper.unwrap(cookie);
            if (itemId != null) {
                launchCookieItemId = itemId;
                break;
            }
        }

        return mActivity.getWorkspace().getFirstMatchForAppClose(launchCookieItemId,
                runningTaskView.getTask().key.getComponent().getPackageName(),
                UserHandle.of(runningTaskView.getTask().key.userId));
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
                    NORMAL, accuracy, StateAnimationConfig.SKIP_ALL_ANIMATIONS);
        }

        @Override
        public void playAtomicAnimation(float velocity) {
            new StaggeredWorkspaceAnim(mActivity, velocity,
                    true /* animateOverviewScrim */).start();
        }

        @Override
        public boolean supportSwipePipToHome() {
            return true;
        }
    }
}
