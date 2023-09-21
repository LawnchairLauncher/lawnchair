/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.unfold.util

import android.content.Context
import android.view.Surface
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import com.android.systemui.unfold.updates.RotationChangeProvider
import com.android.systemui.unfold.updates.RotationChangeProvider.RotationListener

/**
 * [UnfoldTransitionProgressProvider] that emits transition progress only when the display has
 * default rotation or 180 degrees opposite rotation (ROTATION_0 or ROTATION_180). It could be
 * helpful to run the animation only when the display's rotation is perpendicular to the fold.
 */
class NaturalRotationUnfoldProgressProvider(
    private val context: Context,
    private val rotationChangeProvider: RotationChangeProvider,
    unfoldTransitionProgressProvider: UnfoldTransitionProgressProvider
) : UnfoldTransitionProgressProvider {

    private val scopedUnfoldTransitionProgressProvider =
        ScopedUnfoldTransitionProgressProvider(unfoldTransitionProgressProvider)

    private var isNaturalRotation: Boolean = false

    fun init() {
        rotationChangeProvider.addCallback(rotationListener)
        rotationListener.onRotationChanged(context.display.rotation)
    }

    private val rotationListener = RotationListener { rotation ->
        val isNewRotationNatural =
            rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180

        if (isNaturalRotation != isNewRotationNatural) {
            isNaturalRotation = isNewRotationNatural
            scopedUnfoldTransitionProgressProvider.setReadyToHandleTransition(isNewRotationNatural)
        }
    }

    override fun destroy() {
        rotationChangeProvider.removeCallback(rotationListener)
        scopedUnfoldTransitionProgressProvider.destroy()
    }

    override fun addCallback(listener: TransitionProgressListener) {
        scopedUnfoldTransitionProgressProvider.addCallback(listener)
    }

    override fun removeCallback(listener: TransitionProgressListener) {
        scopedUnfoldTransitionProgressProvider.removeCallback(listener)
    }
}
