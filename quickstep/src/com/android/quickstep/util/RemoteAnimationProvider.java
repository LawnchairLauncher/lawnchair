/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep.util;

import android.animation.AnimatorSet;
import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;

import com.android.launcher3.LauncherAnimationRunner;
import com.android.launcher3.LauncherAnimationRunner.RemoteAnimationFactory;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

public abstract class RemoteAnimationProvider {

    RemoteAnimationFactory mAnimationRunner;

    public abstract AnimatorSet createWindowAnimation(RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets);

    ActivityOptions toActivityOptions(Handler handler, long duration, Context context) {
        mAnimationRunner = (transit, appTargets, wallpaperTargets, nonApps, result) ->
                result.setAnimation(createWindowAnimation(appTargets, wallpaperTargets), context);
        final LauncherAnimationRunner wrapper = new LauncherAnimationRunner(
                handler, mAnimationRunner, false /* startAtFrontOfQueue */);
        return ActivityOptionsCompat.makeRemoteAnimation(
                new RemoteAnimationAdapterCompat(wrapper, duration, 0));
    }

    /**
     * @return the target with the lowest opaque layer for a certain app animation, or null.
     */
    public static RemoteAnimationTargetCompat findLowestOpaqueLayerTarget(
            RemoteAnimationTargetCompat[] appTargets, int mode) {
        int lowestLayer = Integer.MAX_VALUE;
        int lowestLayerIndex = -1;
        for (int i = appTargets.length - 1; i >= 0; i--) {
            RemoteAnimationTargetCompat target = appTargets[i];
            if (target.mode == mode && !target.isTranslucent) {
                int layer = target.prefixOrderIndex;
                if (layer < lowestLayer) {
                    lowestLayer = layer;
                    lowestLayerIndex = i;
                }
            }
        }
        return lowestLayerIndex != -1
                ? appTargets[lowestLayerIndex]
                : null;
    }
}
