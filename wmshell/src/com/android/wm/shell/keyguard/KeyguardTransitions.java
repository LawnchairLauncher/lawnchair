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

package com.android.wm.shell.keyguard;

import android.annotation.NonNull;
import android.window.IRemoteTransition;

import com.android.wm.shell.shared.annotations.ExternalThread;

/**
 * Interface exposed to SystemUI Keyguard to register handlers for running
 * animations on keyguard visibility changes.
 *
 * TODO(b/274954192): Merge the occludeTransition and occludeByDream handlers and just let the
 * keyguard handler make the decision on which version it wants to play.
 */
@ExternalThread
public interface KeyguardTransitions {
    /**
     * Registers a set of remote transitions for Keyguard.
     */
    default void register(
            @NonNull IRemoteTransition unlockTransition,
            @NonNull IRemoteTransition appearTransition,
            @NonNull IRemoteTransition occludeTransition,
            @NonNull IRemoteTransition occludeByDreamTransition,
            @NonNull IRemoteTransition unoccludeTransition) {}

    /**
     * Notify whether keyguard has created a remote animation runner for next app launch.
     */
    default void setLaunchingActivityOverLockscreen(boolean isLaunchingActivityOverLockscreen) {}
}
