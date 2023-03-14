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
package com.android.launcher3;

import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.CancellationSignal;
import android.view.RemoteAnimationTarget;

import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.RemoteAnimationProvider;

import java.util.function.BiPredicate;

@TargetApi(Build.VERSION_CODES.P)
public class LauncherInitListener extends ActivityInitListener<Launcher> {

    private RemoteAnimationProvider mRemoteAnimationProvider;

    /**
     * @param onInitListener a callback made when the activity is initialized. The callback should
     *                       return true to continue receiving callbacks (ie. for if the activity is
     *                       recreated).
     */
    public LauncherInitListener(BiPredicate<Launcher, Boolean> onInitListener) {
        super(onInitListener, Launcher.ACTIVITY_TRACKER);
    }

    @Override
    public boolean handleInit(Launcher launcher, boolean alreadyOnHome) {
        if (mRemoteAnimationProvider != null) {
            QuickstepTransitionManager appTransitionManager =
                    ((QuickstepLauncher) launcher).getAppTransitionManager();

            // Set a one-time animation provider. After the first call, this will get cleared.
            // TODO: Probably also check the intended target id.
            CancellationSignal cancellationSignal = new CancellationSignal();
            appTransitionManager.setRemoteAnimationProvider(new RemoteAnimationProvider() {
                @Override
                public AnimatorSet createWindowAnimation(RemoteAnimationTarget[] appTargets,
                        RemoteAnimationTarget[] wallpaperTargets) {

                    // On the first call clear the reference.
                    cancellationSignal.cancel();
                    RemoteAnimationProvider provider = mRemoteAnimationProvider;
                    mRemoteAnimationProvider = null;

                    if (provider != null && launcher.getStateManager().getState().overviewUi) {
                        return provider.createWindowAnimation(appTargets, wallpaperTargets);
                    }
                    return null;
                }
            }, cancellationSignal);
        }
        launcher.deferOverlayCallbacksUntilNextResumeOrStop();
        return super.handleInit(launcher, alreadyOnHome);
    }

    @Override
    public void unregister() {
        mRemoteAnimationProvider = null;
        super.unregister();
    }
}
