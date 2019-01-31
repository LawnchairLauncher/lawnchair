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

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.TouchConsumer.INTERACTION_NORMAL;
import static com.android.quickstep.views.RecentsView.CONTENT_ALPHA;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.quickstep.TouchConsumer.InteractionType;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.util.TransformedRect;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * {@link ActivityControlHelper} for recents when the default launcher is different than the
 * currently running one and apps should interact with the {@link RecentsActivity} as opposed
 * to the in-launcher one.
 */
public final class FallbackActivityControllerHelper implements
        ActivityControlHelper<RecentsActivity> {

    private final ComponentName mHomeComponent;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    public FallbackActivityControllerHelper(ComponentName homeComponent) {
        mHomeComponent = homeComponent;
    }

    @Override
    public void onQuickInteractionStart(RecentsActivity activity, RunningTaskInfo taskInfo,
            boolean activityVisible, TouchInteractionLog touchInteractionLog) {
        QuickScrubController controller = activity.<RecentsView>getOverviewPanel()
                .getQuickScrubController();

        // TODO: match user is as well
        boolean startingFromHome = !activityVisible &&
                (taskInfo == null || Objects.equals(taskInfo.topActivity, mHomeComponent));
        controller.onQuickScrubStart(startingFromHome, this, touchInteractionLog);
        if (activityVisible) {
            mUiHandler.postDelayed(controller::onFinishedTransitionToQuickScrub,
                    OVERVIEW_TRANSITION_MS);
        }
    }

    @Override
    public float getTranslationYForQuickScrub(TransformedRect targetRect, DeviceProfile dp,
            Context context) {
        return 0;
    }

    @Override
    public void executeOnWindowAvailable(RecentsActivity activity, Runnable action) {
        action.run();
    }

    @Override
    public void onTransitionCancelled(RecentsActivity activity, boolean activityVisible) {
        // TODO:
    }

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context,
            @InteractionType int interactionType, TransformedRect outRect) {
        LayoutUtils.calculateFallbackTaskSize(context, dp, outRect.rect);
        if (dp.isVerticalBarLayout()) {
            Rect targetInsets = dp.getInsets();
            int hotseatInset = dp.isSeascape() ? targetInsets.left : targetInsets.right;
            return dp.hotseatBarSizePx + hotseatInset;
        } else {
            return dp.heightPx - outRect.rect.bottom;
        }
    }

    @Override
    public void onSwipeUpComplete(RecentsActivity activity) {
        // TODO:
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
            public Animator createActivityAnimationToHome() {
                Animator anim = ObjectAnimator.ofFloat(recentsView, CONTENT_ALPHA, 0);
                anim.addListener(new AnimationSuccessListener() {
                    @Override
                    public void onAnimationSuccess(Animator animator) {
                        recentsView.startHome();
                    }
                });
                return anim;
            }
        };
    }

    @Override
    public AnimationFactory prepareRecentsUI(RecentsActivity activity, boolean activityVisible,
            boolean animateActivity, Consumer<AnimatorPlaybackController> callback) {
        if (activityVisible) {
            return (transitionLength, interactionType) -> { };
        }

        RecentsView rv = activity.getOverviewPanel();
        rv.setContentAlpha(0);

        return new AnimationFactory() {

            boolean isAnimatingToRecents = false;

            @Override
            public void onRemoteAnimationReceived(RemoteAnimationTargetSet targets) {
                isAnimatingToRecents = targets != null && targets.isAnimatingHome();
                if (!isAnimatingToRecents) {
                    rv.setContentAlpha(1);
                }
                createActivityController(getSwipeUpDestinationAndLength(
                        activity.getDeviceProfile(), activity, INTERACTION_NORMAL,
                        new TransformedRect()), INTERACTION_NORMAL);
            }

            @Override
            public void createActivityController(long transitionLength, int interactionType) {
                if (!isAnimatingToRecents) {
                    return;
                }

                ObjectAnimator anim = ObjectAnimator.ofFloat(rv, CONTENT_ALPHA, 0, 1);
                anim.setDuration(transitionLength).setInterpolator(LINEAR);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.play(anim);
                callback.accept(AnimatorPlaybackController.wrap(animatorSet, transitionLength));
            }
        };
    }

    @Override
    public LayoutListener createLayoutListener(RecentsActivity activity) {
        // We do not change anything as part of layout changes in fallback activity. Return a
        // default layout listener.
        return new LayoutListener() {
            @Override
            public void open() { }

            @Override
            public void setHandler(WindowTransformSwipeHandler handler) { }

            @Override
            public void finish() { }

            @Override
            public void update(boolean shouldFinish, boolean isLongSwipe, RectF currentRect,
                    float cornerRadius) { }
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
    public boolean switchToRecentsIfVisible(boolean fromRecentsButton) {
        return false;
    }

    @Override
    public boolean deferStartingActivity(int downHitTarget) {
        // Always defer starting the activity when using fallback
        return true;
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
    public boolean supportsLongSwipe(RecentsActivity activity) {
        return false;
    }

    @Override
    public LongSwipeHelper getLongSwipeController(RecentsActivity activity, int runningTaskId) {
        return null;
    }

    @Override
    public AlphaProperty getAlphaProperty(RecentsActivity activity) {
        return activity.getDragLayer().getAlphaProperty(0);
    }

    @Override
    public int getContainerType() {
        return LauncherLogProto.ContainerType.SIDELOADED_LAUNCHER;
    }

    @Override
    public boolean isInLiveTileMode() {
        return false;
    }
}
