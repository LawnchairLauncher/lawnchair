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
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.quickstep.fallback.FallbackRecentsView;
import com.android.quickstep.util.TransformParams;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams.Builder;

/**
 * Handles the navigation gestures when a 3rd party launcher is the default home activity.
 */
public class FallbackSwipeHandler extends
        BaseSwipeUpHandlerV2<RecentsActivity, FallbackRecentsView> {

    private FallbackHomeAnimationFactory mActiveAnimationFactory;
    private final boolean mRunningOverHome;

    public FallbackSwipeHandler(Context context, RecentsAnimationDeviceState deviceState,
            TaskAnimationManager taskAnimationManager, GestureState gestureState, long touchTimeMs,
            boolean continuingLastGesture, InputConsumerController inputConsumer) {
        super(context, deviceState, taskAnimationManager, gestureState, touchTimeMs,
                continuingLastGesture, inputConsumer);

        mRunningOverHome = ActivityManagerWrapper.isHomeTask(mGestureState.getRunningTask());
    }

    @Override
    protected HomeAnimationFactory createHomeAnimationFactory(long duration) {
        mActiveAnimationFactory = new FallbackHomeAnimationFactory(duration);
        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, 0, 0);
        mContext.startActivity(new Intent(mGestureState.getHomeIntent()), options.toBundle());
        return mActiveAnimationFactory;
    }

    @Override
    protected boolean handleTaskAppeared(RemoteAnimationTargetCompat appearedTaskTarget) {
        if (mActiveAnimationFactory != null
                && mActiveAnimationFactory.handleHomeTaskAppeared(appearedTaskTarget)) {
            mActiveAnimationFactory = null;
            return false;
        }

        return super.handleTaskAppeared(appearedTaskTarget);
    }

    @Override
    protected void finishRecentsControllerToHome(Runnable callback) {
        mRecentsAnimationController.finish(
                false /* toRecents */, callback, true /* sendUserLeaveHint */);
    }

    @Override
    protected void notifyGestureAnimationStartToRecents() {
        if (mRunningOverHome) {
            mRecentsView.onGestureAnimationStartOnHome(mGestureState.getRunningTask());
        } else {
            super.notifyGestureAnimationStartToRecents();
        }
    }

    private class FallbackHomeAnimationFactory extends HomeAnimationFactory
            implements TransformParams.BuilderProxy {

        private final TransformParams mHomeAlphaParams = new TransformParams();
        private final AnimatedFloat mHomeAlpha = new AnimatedFloat(this::updateHomeAlpha);

        private final long mDuration;
        FallbackHomeAnimationFactory(long duration) {
            super(null);
            mDuration = duration;
        }

        @NonNull
        @Override
        public AnimatorPlaybackController createActivityAnimationToHome() {
            PendingAnimation pa = new PendingAnimation(mDuration);
            pa.setFloat(mHomeAlpha, AnimatedFloat.VALUE, 1, LINEAR);
            return pa.createPlaybackController();
        }

        private void updateHomeAlpha() {
            mHomeAlphaParams.setProgress(mHomeAlpha.value);
            if (mHomeAlphaParams.getTargetSet() != null) {
                mHomeAlphaParams.applySurfaceParams(mHomeAlphaParams.createSurfaceParams(this));
            }
        }

        public boolean handleHomeTaskAppeared(RemoteAnimationTargetCompat appearedTaskTarget) {
            if (appearedTaskTarget.activityType == ACTIVITY_TYPE_HOME) {
                RemoteAnimationTargets targets = new RemoteAnimationTargets(
                        new RemoteAnimationTargetCompat[] {appearedTaskTarget},
                        new RemoteAnimationTargetCompat[0], appearedTaskTarget.mode);
                mHomeAlphaParams.setTargetSet(targets);
                updateHomeAlpha();
                return true;
            }
            return false;
        }

        @Override
        public void onBuildParams(Builder builder, RemoteAnimationTargetCompat app, int targetMode,
                TransformParams params) { }
    }
}
