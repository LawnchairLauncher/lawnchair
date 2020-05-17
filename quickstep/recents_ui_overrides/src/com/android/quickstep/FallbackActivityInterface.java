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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;
import static com.android.quickstep.fallback.RecentsState.BACKGROUND_APP;
import static com.android.quickstep.fallback.RecentsState.DEFAULT;
import static com.android.quickstep.util.WindowSizeStrategy.FALLBACK_RECENTS_SIZE_STRATEGY;
import static com.android.quickstep.views.RecentsView.CONTENT_ALPHA;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;

import android.content.Context;
import android.graphics.Rect;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link BaseActivityInterface} for recents when the default launcher is different than the
 * currently running one and apps should interact with the {@link RecentsActivity} as opposed
 * to the in-launcher one.
 */
public final class FallbackActivityInterface implements
        BaseActivityInterface<RecentsActivity> {

    public FallbackActivityInterface() { }

    @Override
    public void onTransitionCancelled(boolean activityVisible) {
        // TODO:
    }

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect) {
        FALLBACK_RECENTS_SIZE_STRATEGY.calculateTaskSize(context, dp, outRect);
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
    public void onSwipeUpToRecentsComplete() {
        RecentsActivity activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
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

    @Override
    public AnimationFactory prepareRecentsUI(
            boolean activityVisible, Consumer<AnimatorPlaybackController> callback) {
        RecentsActivity activity = getCreatedActivity();
        if (activity == null) {
            return (transitionLength) -> { };
        }

        activity.getStateManager().goToState(BACKGROUND_APP);
        FallbackRecentsView rv = activity.getOverviewPanel();
        rv.setContentAlpha(0);

        return new AnimationFactory() {

            boolean isAnimatingToRecents = false;

            @Override
            public void onRemoteAnimationReceived(RemoteAnimationTargets targets) {
                isAnimatingToRecents = targets != null && targets.isAnimatingHome();
                if (!isAnimatingToRecents) {
                    rv.setContentAlpha(1);
                }
                createActivityInterface(getSwipeUpDestinationAndLength(
                        activity.getDeviceProfile(), activity, new Rect()));
            }

            @Override
            public void createActivityInterface(long transitionLength) {
                PendingAnimation pa = new PendingAnimation(transitionLength * 2);

                if (isAnimatingToRecents) {
                    pa.addFloat(rv, CONTENT_ALPHA, 0, 1, LINEAR);
                }

                pa.addFloat(rv, SCALE_PROPERTY, rv.getMaxScaleForFullScreen(), 1, LINEAR);
                pa.addFloat(rv, FULLSCREEN_PROGRESS, 1, 0, LINEAR);
                AnimatorPlaybackController controller = pa.createPlaybackController();

                // Since we are changing the start position of the UI, reapply the state, at the end
                controller.setEndAction(() -> activity.getStateManager().goToState(
                        controller.getInterpolatedProgress() > 0.5 ? DEFAULT : BACKGROUND_APP));
                callback.accept(controller);
            }
        };
    }

    @Override
    public ActivityInitListener createActivityInitListener(
            Predicate<Boolean> onInitListener) {
        return new ActivityInitListener<>((activity, alreadyOnHome) ->
                onInitListener.test(alreadyOnHome), RecentsActivity.ACTIVITY_TRACKER);
    }

    @Nullable
    @Override
    public RecentsActivity getCreatedActivity() {
        return RecentsActivity.ACTIVITY_TRACKER.getCreatedActivity();
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
        return target.screenSpaceBounds;
    }

    @Override
    public boolean allowMinimizeSplitScreen() {
        // TODO: Remove this once b/77875376 is fixed
        return false;
    }

    @Override
    public int getContainerType() {
        RecentsActivity activity = getCreatedActivity();
        boolean visible = activity != null && activity.isStarted() && activity.hasWindowFocus();
        return visible
                ? LauncherLogProto.ContainerType.OTHER_LAUNCHER_APP
                : LauncherLogProto.ContainerType.APP;
    }

    @Override
    public boolean isInLiveTileMode() {
        return false;
    }

    @Override
    public void onLaunchTaskFailed() {
        // TODO: probably go back to overview instead.
        RecentsActivity activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        activity.<RecentsView>getOverviewPanel().startHome();
    }

    @Override
    public void onLaunchTaskSuccess() {
        RecentsActivity activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        activity.onTaskLaunched();
    }
}
