/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.android.quickstep.util.RemoteAnimationProvider.prepareTargetsForFirstFrame;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;

import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.TransactionCompat;

/**
 * Animation listener which fades out the closing targets
 */
public class RemoteFadeOutAnimationListener implements AnimatorUpdateListener {

    private final RemoteAnimationTargetSet mTarget;
    private boolean mFirstFrame = true;

    public RemoteFadeOutAnimationListener(RemoteAnimationTargetCompat[] targets) {
        mTarget = new RemoteAnimationTargetSet(targets, MODE_CLOSING);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        TransactionCompat t = new TransactionCompat();
        if (mFirstFrame) {
            prepareTargetsForFirstFrame(mTarget.unfilteredApps, t, MODE_CLOSING);
            mFirstFrame = false;
        }

        float alpha = 1 - valueAnimator.getAnimatedFraction();
        for (RemoteAnimationTargetCompat app : mTarget.apps) {
            t.setAlpha(app.leash, alpha);
        }
        t.apply();
    }
}
