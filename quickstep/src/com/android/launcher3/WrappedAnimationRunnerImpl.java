/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.os.Handler;

import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Used with WrappedLauncherAnimationRunner as an interface for the runner to call back to the
 * implementation.
 */
public interface WrappedAnimationRunnerImpl {
    Handler getHandler();
    void onCreateAnimation(RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets,
            LauncherAnimationRunner.AnimationResult result);
}
