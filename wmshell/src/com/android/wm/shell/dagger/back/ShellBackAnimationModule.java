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

package com.android.wm.shell.dagger.back;

import com.android.wm.shell.back.CrossTaskBackAnimation;
import com.android.wm.shell.back.CustomCrossActivityBackAnimation;
import com.android.wm.shell.back.DefaultCrossActivityBackAnimation;
import com.android.wm.shell.back.ShellBackAnimation;
import com.android.wm.shell.back.ShellBackAnimationRegistry;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

/** Default animation definitions for predictive back. */
@Module
public interface ShellBackAnimationModule {
    /** Default animation registry */
    @Provides
    static ShellBackAnimationRegistry provideBackAnimationRegistry(
            @ShellBackAnimation.CrossActivity ShellBackAnimation crossActivity,
            @ShellBackAnimation.CrossTask ShellBackAnimation crossTask,
            @ShellBackAnimation.CustomizeActivity ShellBackAnimation customizeActivity) {
        return new ShellBackAnimationRegistry(
                crossActivity,
                crossTask,
                /* dialogCloseAnimation */ null,
                customizeActivity,
                /* defaultBackToHomeAnimation= */ null);
    }

    /** Default cross activity back animation */
    @Binds
    @ShellBackAnimation.CrossActivity
    ShellBackAnimation bindCrossActivityShellBackAnimation(
            DefaultCrossActivityBackAnimation defaultCrossActivityBackAnimation);

    /** Default cross task back animation */
    @Binds
    @ShellBackAnimation.CrossTask
    ShellBackAnimation provideCrossTaskShellBackAnimation(
            CrossTaskBackAnimation crossTaskBackAnimation);

    /** Default customized activity back animation */
    @Binds
    @ShellBackAnimation.CustomizeActivity
    ShellBackAnimation provideCustomizeActivityShellBackAnimation(
            CustomCrossActivityBackAnimation customCrossActivityBackAnimation);
}
