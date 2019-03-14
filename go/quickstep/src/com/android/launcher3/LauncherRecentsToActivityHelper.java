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
package com.android.launcher3;

import static com.android.launcher3.LauncherState.NORMAL;

import com.android.quickstep.RecentsToActivityHelper;

/**
 * {@link RecentsToActivityHelper} for when the recents implementation is contained in
 * {@link Launcher}.
 */
public final class LauncherRecentsToActivityHelper implements RecentsToActivityHelper {

    private final Launcher mLauncher;

    public LauncherRecentsToActivityHelper(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void leaveRecents() {
        mLauncher.getStateManager().goToState(NORMAL);
    }
}
