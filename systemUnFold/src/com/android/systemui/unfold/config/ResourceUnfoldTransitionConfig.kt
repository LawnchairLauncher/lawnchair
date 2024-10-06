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
package com.android.systemui.unfold.config

import android.content.res.Resources
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceUnfoldTransitionConfig @Inject constructor() : UnfoldTransitionConfig {

    private fun getBooleanResource(resourceName: String): Boolean {
        val id = Resources.getSystem().getIdentifier(resourceName, "bool", "android")
        return if (id != 0) {
            Resources.getSystem().getBoolean(id)
        } else {
            false
        }
    }

    private fun getIntResource(resourceName: String): Int {
        val id = Resources.getSystem().getIdentifier(resourceName, "integer", "android")
        return if (id != 0) {
            Resources.getSystem().getInteger(id)
        } else {
            0
        }
    }

    override val isEnabled: Boolean by lazy {
        getBooleanResource("config_unfoldTransitionEnabled")
    }

    override val isHingeAngleEnabled: Boolean by lazy {
        getBooleanResource("config_unfoldTransitionHingeAngle")
    }

    override val isHapticsEnabled: Boolean by lazy {
        getBooleanResource("config_unfoldTransitionHapticsEnabled")
    }

    override val halfFoldedTimeoutMillis: Int by lazy {
        getIntResource("config_unfoldTransitionHalfFoldedTimeout")
    }
}
