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

package com.android.quickstep.util

import android.graphics.RectF
import android.view.View
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.AsyncView
import com.android.launcher3.views.FloatingIconView

/**
 * Helper class for floating icon view instance that encapsulates code that should not be exposed
 * for public use.
 */
object FloatingIconViewHelper {

    /**
     * Uses [FloatingIconView.getFloatingIconView], but also offsets the [positionOut] for the
     * hotseat if hotseat is shrank due to the bubble bar presence.
     */
    @JvmStatic
    fun getFloatingIconView(
        launcher: QuickstepLauncher,
        originalView: View,
        visibilitySyncView: AsyncView?,
        fadeOutView: AsyncView?,
        hideOriginal: Boolean,
        positionOut: RectF,
        isOpening: Boolean,
    ): FloatingIconView {
        val floatingIconView =
            FloatingIconView.getFloatingIconView(
                launcher,
                originalView,
                visibilitySyncView,
                fadeOutView,
                hideOriginal,
                positionOut,
                isOpening,
            )
        if (!isOpening) {
            launcher.offsetBoundsXToHotseatIfApplicable(positionOut, originalView)
        }
        return floatingIconView
    }
}
