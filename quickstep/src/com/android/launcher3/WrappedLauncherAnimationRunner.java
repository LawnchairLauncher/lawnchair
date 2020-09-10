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

import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.lang.ref.WeakReference;

/**
 * This class is needed to wrap any animation runner that is a part of the
 * RemoteAnimationDefinition:
 * - Launcher creates a new instance of the LauncherAppTransitionManagerImpl whenever it is
 *   created, which in turn registers a new definition
 * - When the definition is registered, window manager retains a strong binder reference to the
 *   runner passed in
 * - If the Launcher activity is recreated, the new definition registered will replace the old
 *   reference in the system's activity record, but until the system server is GC'd, the binder
 *   reference will still exist, which references the runner in the Launcher process, which
 *   references the (old) Launcher activity through this class
 *
 * Instead we make the runner provided to the definition static only holding a weak reference to
 * the runner implementation.  When this animation manager is destroyed, we remove the Launcher
 * reference to the runner, leaving only the weak ref from the runner.
 */
public class WrappedLauncherAnimationRunner<R extends WrappedAnimationRunnerImpl>
        extends LauncherAnimationRunner {
    private WeakReference<R> mImpl;

    public WrappedLauncherAnimationRunner(R animationRunnerImpl, boolean startAtFrontOfQueue) {
        super(animationRunnerImpl.getHandler(), startAtFrontOfQueue);
        mImpl = new WeakReference<>(animationRunnerImpl);
    }

    @Override
    public void onCreateAnimation(RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets, AnimationResult result) {
        R animationRunnerImpl = mImpl.get();
        if (animationRunnerImpl != null) {
            animationRunnerImpl.onCreateAnimation(appTargets, wallpaperTargets, result);
        }
    }
}
