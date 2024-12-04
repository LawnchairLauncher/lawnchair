/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.back;

import android.content.res.Configuration;
import android.window.BackNavigationInfo;

import javax.inject.Qualifier;

/** Base class for all back animations. */
public abstract class ShellBackAnimation {
    @Qualifier
    public @interface CrossActivity {}

    @Qualifier
    public @interface CrossTask {}

    @Qualifier
    public @interface CustomizeActivity {}

    @Qualifier
    public @interface ReturnToHome {}

    @Qualifier
    public @interface DialogClose {}

    /** Retrieve the {@link BackAnimationRunner} associated with this animation. */
    public abstract BackAnimationRunner getRunner();

    /**
     * Prepare the next animation.
     *
     * @return true if this type of back animation should override the default.
     */
    public boolean prepareNextAnimation(BackNavigationInfo.CustomAnimationInfo animationInfo,
            int letterboxColor) {
        return false;
    }

    void onConfigurationChanged(Configuration newConfig) {

    }
}
