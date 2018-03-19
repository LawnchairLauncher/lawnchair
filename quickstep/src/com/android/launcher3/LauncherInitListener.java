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

import static com.android.launcher3.states.RotationHelper.REQUEST_LOCK;

import android.annotation.TargetApi;
import android.os.Build;

import com.android.launcher3.states.InternalStateHandler;
import com.android.quickstep.ActivityControlHelper.ActivityInitListener;

import java.util.function.BiPredicate;

@TargetApi(Build.VERSION_CODES.P)
public class LauncherInitListener extends InternalStateHandler implements ActivityInitListener {

    private final BiPredicate<Launcher, Boolean> mOnInitListener;

    public LauncherInitListener(BiPredicate<Launcher, Boolean> onInitListener) {
        mOnInitListener = onInitListener;
    }

    @Override
    protected boolean init(Launcher launcher, boolean alreadyOnHome) {
        // For the duration of the gesture, lock the screen orientation to ensure that we do not
        // rotate mid-quickscrub
        launcher.getRotationHelper().setStateHandlerRequest(REQUEST_LOCK);
        return mOnInitListener.test(launcher, alreadyOnHome);
    }

    @Override
    public void register() {
        initWhenReady();
    }

    @Override
    public void unregister() {
        clearReference();
    }
}
