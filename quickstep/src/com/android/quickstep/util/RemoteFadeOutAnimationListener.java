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

import static android.view.RemoteAnimationTarget.MODE_CLOSING;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl.Transaction;

import com.android.quickstep.RemoteAnimationTargets;

/**
 * Animation listener which fades out the closing targets
 */
public class RemoteFadeOutAnimationListener implements AnimatorUpdateListener {

    private final RemoteAnimationTargets mTarget;
    private boolean mFirstFrame = true;

    public RemoteFadeOutAnimationListener(RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpaperTargets) {
        mTarget = new RemoteAnimationTargets(appTargets, wallpaperTargets,
                new RemoteAnimationTarget[0], MODE_CLOSING);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        Transaction t = new Transaction();
        if (mFirstFrame) {
            for (RemoteAnimationTarget target : mTarget.unfilteredApps) {
                t.show(target.leash);
            }
            mFirstFrame = false;
        }

        float alpha = 1 - valueAnimator.getAnimatedFraction();
        for (RemoteAnimationTarget app : mTarget.apps) {
            t.setAlpha(app.leash, alpha);
        }
        t.apply();
    }
}
