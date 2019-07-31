/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;
import static com.android.quickstep.fallback.FallbackRecentsView.ZOOM_PROGRESS;
import static com.android.quickstep.views.RecentsView.CONTENT_ALPHA;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * {@link ActivityControlHelper} for recents when the default launcher is different than the
 * currently running one and apps should interact with the {@link RecentsActivity} as opposed
 * to the in-launcher one.
 */
public final class FallbackActivityControllerHelper implements
        ActivityControlHelper<RecentsActivity> {

    public FallbackActivityControllerHelper() { }

    @Override
    public void onTransitionCancelled(RecentsActivity activity, boolean activityVisible) {
        // TODO:
    }

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect) {
        LayoutUtils.calculateFallbackTaskSize(context, dp, outRect);
        if (dp.isVerticalBarLayout()
                && SysUINavigationMode.INSTANCE.get(context).getMode() != NO_BUTTON) {
            Rect targetInsets = dp.getInsets();
            int hotseatInset = dp.isSeascape() ? targetInsets.left : targetInsets.right;
            return dp.hotseatBarSizePx + hotseatInset;
        } else {
            return dp.heightPx - outRect.bottom;
        }
    }

    @Override
    public void onSwipeUpToRecentsComplete(RecentsActivity activity) {
        RecentsView recentsView = activity.getOverviewPanel();
        recentsView.getClearAllButton().setVisibilityAlpha(1);
        recentsView.setDisallowScrollToClearAll(false);
    }

    @Override
    public void onAssistantVisibilityChanged(float visibility) {
        // This class becomes active when the screen is locked.
        // Rather than having it handle assistant visibility changes, the assistant visibility is
        // set to zero prior to this class becoming active.
    }

    @NonNull
    @Override
    public HomeAnimationFactory prepareHomeUI(RecentsActivity activity) {
        RecentsView recentsView = activity.getOverviewPanel();

        return new HomeAnimationFactory() {
            @NonNull
            @Override
            public RectF getWindowTargetRect() {
                float centerX = recentsView.getPivotX();
                float centerY = recentsView.getPivotY();
                return new RectF(centerX, centerY, centerX, centerY);
            }

            @NonNull
            @Override
            public AnimatorPlaybackController createActivityAnimationToHome() {
                Animator anim = ObjectAnimator.ofFloat(recentsView, CONTENT_ALPHA, 0);
                anim.addListener(new AnimationSuccessListener() {
                    @Override
                    public void onAnimationSuccess(Animator animator) {
                        recentsView.startHome();
                    }
                });
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.play(anim);
                long accuracy = 2 * Math.max(recentsView.getWidth(), recentsView.getHeight());
                return AnimatorPlaybackController.wrap(animatorSet, accuracy);
            }
        };
    }

    @Override
    public AnimationFactory prepareRecentsUI(RecentsActivity activity, boolean activityVisible,
            boolean animateActivity, Consumer<AnimatorPlaybackController> callback) {
        if (activityVisible) {
            return (transitionLength) -> { };
        }

        FallbackRecentsView rv = activity.getOverviewPanel();
        rv.setContentAlpha(0);
        rv.getClearAllButton().setVisibilityAlpha(0);
        rv.setDisallowScrollToClearAll(true);

        boolean fromState = !animateActivity;
        rv.setInOverviewState(fromState);

        return new AnimationFactory() {

            boolean isAnimatingToRecents = false;

            @Override
            public void onRemoteAnimationReceived(RemoteAnimationTargetSet targets) {
                isAnimatingToRecents = targets != null && targets.isAnimatingHome();
                if (!isAnimatingToRecents) {
                    rv.setContentAlpha(1);
                }
                createActivityController(getSwipeUpDestinationAndLength(
                        activity.getDeviceProfile(), activity, new Rect()));
            }

            @Override
            public void createActivityController(long transitionLength) {
                AnimatorSet animatorSet = new AnimatorSet();
                if (isAnimatingToRecents) {
                    ObjectAnimator anim = ObjectAnimator.ofFloat(rv, CONTENT_ALPHA, 0, 1);
                    anim.setDuration(transitionLength).setInterpolator(LINEAR);
                    animatorSet.play(anim);
                }

                ObjectAnimator anim = ObjectAnimator.ofFloat(rv, ZOOM_PROGRESS, 1, 0);
                anim.setDuration(transitionLength).setInterpolator(LINEAR);
                animatorSet.play(anim);

                AnimatorPlaybackController controller =
                        AnimatorPlaybackController.wrap(animatorSet, transitionLength);

                // Since we are changing the start position of the UI, reapply the state, at the end
                controller.setEndAction(() -> {
                    boolean endState = true;
                    rv.setInOverviewState(controller.getInterpolatedProgress() > 0.5 ?
                                    endState : fromState);
                });

                callback.accept(controller);
            }
        };
    }

    @Override
    public ActivityInitListener createActivityInitListener(
            BiPredicate<RecentsActivity, Boolean> onInitListener) {
        return new RecentsActivityTracker(onInitListener);
    }

    @Nullable
    @Override
    public RecentsActivity getCreatedActivity() {
        return RecentsActivityTracker.getCurrentActivity();
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        RecentsActivity activity = getCreatedActivity();
        if (activity != null && activity.hasWindowFocus()) {
            return activity.getOverviewPanel();
        }
        return null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Runnable onCompleteCallback) {
        return false;
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target) {
        // TODO: Remove this once b/77875376 is fixed
        return target.sourceContainerBounds;
    }

    @Override
    public boolean shouldMinimizeSplitScreen() {
        // TODO: Remove this once b/77875376 is fixed
        return false;
    }

    @Override
    public int getContainerType() {
        RecentsActivity activity = getCreatedActivity();
        boolean visible = activity != null && activity.isStarted() && activity.hasWindowFocus();
        return visible
                ? LauncherLogProto.ContainerType.SIDELOADED_LAUNCHER
                : LauncherLogProto.ContainerType.APP;
    }

    @Override
    public boolean isInLiveTileMode() {
        return false;
    }

    @Override
    public void onLaunchTaskFailed(RecentsActivity activity) {
        // TODO: probably go back to overview instead.
        activity.<RecentsView>getOverviewPanel().startHome();
    }

    @Override
    public void onLaunchTaskSuccess(RecentsActivity activity) {
        activity.onTaskLaunched();
    }
}
