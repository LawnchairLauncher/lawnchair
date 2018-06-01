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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;

import com.android.launcher3.states.InternalStateHandler;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;
import com.android.quickstep.OverviewCallbacks;
import com.android.quickstep.util.RemoteAnimationProvider;

import java.util.function.BiPredicate;

@TargetApi(Build.VERSION_CODES.P)
public class LauncherInitListener extends InternalStateHandler implements ActivityInitListener {

    private final BiPredicate<Launcher, Boolean> mOnInitListener;

    private RemoteAnimationProvider mRemoteAnimationProvider;

    public LauncherInitListener(BiPredicate<Launcher, Boolean> onInitListener) {
        mOnInitListener = onInitListener;
    }

    @Override
    protected boolean init(Launcher launcher, boolean alreadyOnHome) {
        if (mRemoteAnimationProvider != null) {
            LauncherAppTransitionManagerImpl appTransitionManager =
                    (LauncherAppTransitionManagerImpl) launcher.getAppTransitionManager();

            // Set a one-time animation provider. After the first call, this will get cleared.
            // TODO: Probably also check the intended target id.
            CancellationSignal cancellationSignal = new CancellationSignal();
            appTransitionManager.setRemoteAnimationProvider((targets) -> {

                // On the first call clear the reference.
                cancellationSignal.cancel();
                RemoteAnimationProvider provider = mRemoteAnimationProvider;
                mRemoteAnimationProvider = null;

                if (provider != null && launcher.getStateManager().getState().overviewUi) {
                    return provider.createWindowAnimation(targets);
                }
                return null;
            }, cancellationSignal);
        }
        OverviewCallbacks.get(launcher).onInitOverviewTransition();
        return mOnInitListener.test(launcher, alreadyOnHome);
    }

    @Override
    public void register() {
        initWhenReady();
    }

    @Override
    public void unregister() {
        mRemoteAnimationProvider = null;
        clearReference();
    }

    @Override
    public void registerAndStartActivity(Intent intent, RemoteAnimationProvider animProvider,
            Context context, Handler handler, long duration) {
        mRemoteAnimationProvider = animProvider;

        register();

        Bundle options = animProvider.toActivityOptions(handler, duration).toBundle();
        context.startActivity(addToIntent(new Intent((intent))), options);
    }
}
