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
import android.view.RemoteAnimationTarget;

public abstract class RemoteAnimationProvider {

    public abstract AnimatorSet createWindowAnimation(RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpaperTargets);

    /**
     * @return the target with the lowest opaque layer for a certain app animation, or null.
     */
    public static RemoteAnimationTarget findLowestOpaqueLayerTarget(
            RemoteAnimationTarget[] appTargets, int mode) {
        int lowestLayer = Integer.MAX_VALUE;
        int lowestLayerIndex = -1;
        for (int i = appTargets.length - 1; i >= 0; i--) {
            RemoteAnimationTarget target = appTargets[i];
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
