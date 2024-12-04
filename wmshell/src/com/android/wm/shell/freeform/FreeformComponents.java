/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.freeform;

import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.provider.Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT;

import android.content.Context;
import android.provider.Settings;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.transition.Transitions;

import java.util.Optional;

/**
 * Class that holds freeform related classes. It serves as the single injection point of
 * all freeform classes to avoid leaking implementation details to the base Dagger module.
 */
public class FreeformComponents {
    public final ShellTaskOrganizer.TaskListener mTaskListener;
    public final Optional<Transitions.TransitionHandler> mTransitionHandler;
    public final Optional<Transitions.TransitionObserver> mTransitionObserver;

    /**
     * Creates an instance with the given components.
     */
    public FreeformComponents(
            ShellTaskOrganizer.TaskListener taskListener,
            Optional<Transitions.TransitionHandler> transitionHandler,
            Optional<Transitions.TransitionObserver> transitionObserver) {
        mTaskListener = taskListener;
        mTransitionHandler = transitionHandler;
        mTransitionObserver = transitionObserver;
    }

    /**
     * Returns if this device supports freeform.
     */
    public static boolean isFreeformEnabled(Context context) {
        return context.getPackageManager().hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                || Settings.Global.getInt(context.getContentResolver(),
                DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0) != 0;
    }
}
