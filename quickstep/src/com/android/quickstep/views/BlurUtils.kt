/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep.views

import com.android.launcher3.Flags.enableOverviewBackgroundWallpaperBlur
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle

/** Applies blur either behind launcher surface or live tile app. */
class BlurUtils(private val recentsView: RecentsView<*, *>) {

    fun setDrawLiveTileBelowRecents(drawBelowRecents: Boolean) {
        val liveTileRemoteTargetHandles =
            if (
                recentsView.remoteTargetHandles != null &&
                    recentsView.recentsAnimationController != null
            )
                recentsView.remoteTargetHandles
            else null
        setDrawBelowRecents(drawBelowRecents, liveTileRemoteTargetHandles)
    }

    /**
     * Set surface in [remoteTargetHandles] to be above or below Recents layer, and update the base
     * layer to apply blur to in BaseDepthController.
     */
    fun setDrawBelowRecents(
        drawBelowRecents: Boolean,
        remoteTargetHandles: Array<RemoteTargetHandle>? = null,
    ) {
        remoteTargetHandles?.forEach { it.taskViewSimulator.setDrawsBelowRecents(drawBelowRecents) }
        if (enableOverviewBackgroundWallpaperBlur()) {
            recentsView.depthController?.setBaseSurfaceOverride(
                // Blurs behind launcher layer.
                if (!drawBelowRecents || remoteTargetHandles == null) {
                    null
                } else {
                    // Blurs behind live tile. blur will be applied behind window
                    // which farthest from user in case of desktop and split apps.
                    remoteTargetHandles
                        .maxByOrNull { it.transformParams.targetSet.firstAppTarget.leash.layerId }
                        ?.transformParams
                        ?.targetSet
                        ?.firstAppTarget
                        ?.leash
                }
            )
        }
    }
}
