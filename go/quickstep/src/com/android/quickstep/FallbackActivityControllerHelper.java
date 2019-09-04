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
import static com.android.quickstep.views.IconRecentsView.CONTENT_ALPHA;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Rect;

import androidx.annotation.Nullable;

import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.views.IconRecentsView;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * {@link ActivityControlHelper} for recents when the default launcher is different than the
 * currently running one and apps should interact with the {@link RecentsActivity} as opposed
 * to the in-launcher one.
 */
public final class FallbackActivityControllerHelper extends
        GoActivityControlHelper<RecentsActivity> {

    public FallbackActivityControllerHelper() { }

    @Override
    public AnimationFactory prepareRecentsUI(RecentsActivity activity, boolean activityVisible,
            boolean animateActivity, Consumer<AnimatorPlaybackController> callback) {
        if (activityVisible) {
            return (transitionLength) -> { };
        }

        IconRecentsView rv = activity.getOverviewPanel();
        rv.setUsingRemoteAnimation(true);
        rv.setAlpha(0);

        return new AnimationFactory() {

            boolean isAnimatingToRecents = false;

            @Override
            public void onRemoteAnimationReceived(RemoteAnimationTargetSet targets) {
                isAnimatingToRecents = targets != null && targets.isAnimatingHome();
                if (!isAnimatingToRecents) {
                    rv.setAlpha(1);
                }
                createActivityController(getSwipeUpDestinationAndLength(
                        activity.getDeviceProfile(), activity, new Rect()));
            }

            @Override
            public void createActivityController(long transitionLength) {
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
    public IconRecentsView getVisibleRecentsView() {
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
    public int getContainerType() {
        return LauncherLogProto.ContainerType.SIDELOADED_LAUNCHER;
    }

    @Override
    public void onLaunchTaskSuccess(RecentsActivity activity) { }
}
