/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.dagger;

import android.annotation.NonNull;

import com.android.wm.shell.compatui.letterbox.DelegateLetterboxTransitionObserver;
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy;
import com.android.wm.shell.compatui.letterbox.MixedLetterboxController;
import com.android.wm.shell.compatui.letterbox.config.LetterboxDependenciesHelper;
import com.android.wm.shell.compatui.letterbox.lifecycle.ActivityLetterboxLifecycleEventFactory;
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleController;
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleControllerImpl;
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventFactory;
import com.android.wm.shell.compatui.letterbox.lifecycle.MultiLetterboxLifecycleEventFactory;
import com.android.wm.shell.compatui.letterbox.lifecycle.SkipLetterboxLifecycleEventFactory;
import com.android.wm.shell.compatui.letterbox.lifecycle.TaskIdResolver;
import com.android.wm.shell.compatui.letterbox.lifecycle.TaskInfoLetterboxLifecycleEventFactory;
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoRepository;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import dagger.Module;
import dagger.Provides;

import java.util.List;

/**
 * Provides Letterbox Shell implementation components to Dagger dependency Graph.
 */
@Module
public abstract class LetterboxModule {

    @WMSingleton
    @Provides
    static DelegateLetterboxTransitionObserver provideDelegateLetterboxTransitionObserver(
            @NonNull ShellInit shellInit,
            @NonNull Transitions transitions,
            @NonNull LetterboxLifecycleController letterboxLifecycleController,
            @NonNull LetterboxLifecycleEventFactory letterboxLifecycleEventFactory
    ) {
        return new DelegateLetterboxTransitionObserver(shellInit, transitions,
                letterboxLifecycleController, letterboxLifecycleEventFactory);
    }

    @WMSingleton
    @Provides
    static LetterboxLifecycleEventFactory provideLetterboxLifecycleEventFactory(
            @NonNull SkipLetterboxLifecycleEventFactory skipLetterboxLifecycleEventFactory,
            @NonNull LetterboxTaskInfoRepository letterboxTaskInfoRepository,
            @NonNull LetterboxDependenciesHelper letterboxDependenciesHelper,
            @NonNull TaskIdResolver taskIdResolver
    ) {
        // The order of the LetterboxLifecycleEventFactory implementation matters because the
        // first that can handle a Change will be chosen for the LetterboxLifecycleEvent creation.
        return new MultiLetterboxLifecycleEventFactory(List.of(
                // Filters out transitions not related to Letterboxing.
                skipLetterboxLifecycleEventFactory,
                // Handle Transition for Activities
                new ActivityLetterboxLifecycleEventFactory(letterboxTaskInfoRepository,
                        letterboxDependenciesHelper),
                // Creates a LetterboxLifecycleEvent in case of Task transitions.
                new TaskInfoLetterboxLifecycleEventFactory(letterboxDependenciesHelper,
                        letterboxTaskInfoRepository, taskIdResolver)
        ));
    }

    @WMSingleton
    @Provides
    static LetterboxLifecycleController provideLetterboxLifecycleController(
            @NonNull MixedLetterboxController letterboxController,
            @NonNull LetterboxControllerStrategy letterboxControllerStrategy
    ) {
        return new LetterboxLifecycleControllerImpl(letterboxController,
                letterboxControllerStrategy);
    }
}
