/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.animation.shared;

import android.window.RemoteTransition;

/**
 * An interface for an app to link a launch transition and a return transition together into an
 * origin transition.
 */
interface IOriginTransitions {

    /**
     * Create a new "origin transition" which wraps a launch transition and a return transition.
     * The returned {@link RemoteTransition} is expected to be passed to
     * {@link ActivityOptions#makeRemoteTransition(RemoteTransition)} to create an
     * {@link ActivityOptions} and being used to launch an intent. When being used with
     * {@link ActivityOptions}, the launch transition will be triggered for launching the intent,
     * and the return transition will be remembered and triggered for returning from the launched
     * activity.
     */
    RemoteTransition makeOriginTransition(in RemoteTransition launchTransition,
            in RemoteTransition returnTransition) = 1;

    /**
     * Cancels an origin transition. Any parts not yet played will no longer be triggered, and the
     * origin transition object will reset to a single frame animation.
     */
    void cancelOriginTransition(in RemoteTransition originTransition) = 2;
}
