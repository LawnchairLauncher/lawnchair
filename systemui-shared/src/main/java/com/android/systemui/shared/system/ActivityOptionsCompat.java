/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.app.ActivityManager.SPLIT_SCREEN_CREATE_MODE_BOTTOM_OR_RIGHT;
import static android.app.ActivityManager.SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;

import android.app.ActivityOptions;

/**
 * Wrapper around internal ActivityOptions creation.
 */
public abstract class ActivityOptionsCompat {

    /**
     * @return ActivityOptions for starting a task in split screen.
     */
    public static ActivityOptions makeSplitScreenOptions(boolean dockTopLeft) {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);
        options.setSplitScreenCreateMode(dockTopLeft
                ? SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT
                : SPLIT_SCREEN_CREATE_MODE_BOTTOM_OR_RIGHT);
        return options;
    }

    public static ActivityOptions makeRemoteAnimation(
            RemoteAnimationAdapterCompat remoteAnimationAdapter) {
        return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter.getWrapped());
    }
}
