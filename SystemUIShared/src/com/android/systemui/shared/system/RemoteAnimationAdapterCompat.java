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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.os.RemoteException;
import android.util.Log;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;

/**
 * @see RemoteAnimationAdapter
 */
public class RemoteAnimationAdapterCompat {

    private final RemoteAnimationAdapter mWrapped;

    public RemoteAnimationAdapterCompat(RemoteAnimationRunnerCompat runner, long duration,
            long statusBarTransitionDelay) {
        mWrapped = new RemoteAnimationAdapter(wrapRemoteAnimationRunner(runner), duration,
                statusBarTransitionDelay);
    }

    RemoteAnimationAdapter getWrapped() {
        return mWrapped;
    }

    private static IRemoteAnimationRunner.Stub wrapRemoteAnimationRunner(
            final RemoteAnimationRunnerCompat remoteAnimationAdapter) {
        return new IRemoteAnimationRunner.Stub() {
            @Override
            public void onAnimationStart(RemoteAnimationTarget[] apps,
                    RemoteAnimationTarget[] wallpapers,
                    final IRemoteAnimationFinishedCallback finishedCallback) {
                final RemoteAnimationTargetCompat[] appsCompat =
                        RemoteAnimationTargetCompat.wrap(apps);
                final RemoteAnimationTargetCompat[] wallpapersCompat =
                        RemoteAnimationTargetCompat.wrap(wallpapers);
                final Runnable animationFinishedCallback = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            finishedCallback.onAnimationFinished();
                        } catch (RemoteException e) {
                            Log.e("ActivityOptionsCompat", "Failed to call app controlled animation"
                                    + " finished callback", e);
                        }
                    }
                };
                remoteAnimationAdapter.onAnimationStart(appsCompat, wallpapersCompat,
                        animationFinishedCallback);
            }

            @Override
            public void onAnimationCancelled() {
                remoteAnimationAdapter.onAnimationCancelled();
            }
        };
    }
}
