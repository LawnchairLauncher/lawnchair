/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.unfold.config

import android.content.Context
import android.os.Build
import android.os.SystemProperties
import com.android.systemui.shared.recents.utilities.Utilities

internal class ResourceUnfoldTransitionConfig(
    private val context: Context
) : UnfoldTransitionConfig {

    override val isEnabled: Boolean
        get() = readIsEnabledResource() && isPropertyEnabled

    override val isHingeAngleEnabled: Boolean
        get() = readIsHingeAngleEnabled()

    private val isPropertyEnabled: Boolean
        get() = SystemProperties.getInt(UNFOLD_TRANSITION_MODE_PROPERTY_NAME,
            UNFOLD_TRANSITION_PROPERTY_ENABLED) == UNFOLD_TRANSITION_PROPERTY_ENABLED

    private fun readIsEnabledResource(): Boolean {
        if (!Utilities.ATLEAST_S_V2) return false
        return context.resources
            .getBoolean(com.android.internal.R.bool.config_unfoldTransitionEnabled)
    }

    private fun readIsHingeAngleEnabled(): Boolean {
        if (!Utilities.ATLEAST_S_V2) return false
        return context.resources
            .getBoolean(com.android.internal.R.bool.config_unfoldTransitionHingeAngle)
    }
}

/**
 * Temporary persistent property to control unfold transition mode
 * See [com.android.unfold.config.AnimationMode]
 */
private const val UNFOLD_TRANSITION_MODE_PROPERTY_NAME = "persist.unfold.transition_enabled"
private const val UNFOLD_TRANSITION_PROPERTY_ENABLED = 1
