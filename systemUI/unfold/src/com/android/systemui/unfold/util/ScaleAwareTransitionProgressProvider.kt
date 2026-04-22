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

import android.content.ContentResolver
import android.database.ContentObserver
import android.provider.Settings
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** Wraps [UnfoldTransitionProgressProvider] to disable transitions when animations are disabled. */
class ScaleAwareTransitionProgressProvider
@AssistedInject
constructor(
    @Assisted progressProviderToWrap: UnfoldTransitionProgressProvider,
    private val contentResolver: ContentResolver
) : UnfoldTransitionProgressProvider {

    private val scopedUnfoldTransitionProgressProvider =
        ScopedUnfoldTransitionProgressProvider(progressProviderToWrap)

    private val animatorDurationScaleObserver =
        object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                onAnimatorScaleChanged()
            }
        }

    init {
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            /* notifyForDescendants= */ false,
            animatorDurationScaleObserver
        )
        onAnimatorScaleChanged()
    }

    private fun onAnimatorScaleChanged() {
        scopedUnfoldTransitionProgressProvider.setReadyToHandleTransition(
            contentResolver.areAnimationsEnabled()
        )
    }

    override fun addCallback(listener: TransitionProgressListener) {
        scopedUnfoldTransitionProgressProvider.addCallback(listener)
    }

    override fun removeCallback(listener: TransitionProgressListener) {
        scopedUnfoldTransitionProgressProvider.removeCallback(listener)
    }

    override fun destroy() {
        contentResolver.unregisterContentObserver(animatorDurationScaleObserver)
        scopedUnfoldTransitionProgressProvider.destroy()
    }

    @AssistedFactory
    interface Factory {
        fun wrap(
            progressProvider: UnfoldTransitionProgressProvider
        ): ScaleAwareTransitionProgressProvider
    }

    companion object {
        fun ContentResolver.areAnimationsEnabled(): Boolean {
            val animationScale =
                Settings.Global.getString(
                        this,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                    )
                    ?.toFloatOrNull()
                    ?: 1f
            return animationScale != 0f
        }
    }
}
