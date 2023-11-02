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

    override val isEnabled: Boolean by lazy {
        val id = Resources.getSystem()
            .getIdentifier("config_unfoldTransitionEnabled", "bool", "android")
        Resources.getSystem().getBoolean(id)
    }

    override val isHingeAngleEnabled: Boolean by lazy {
        val id = Resources.getSystem()
            .getIdentifier("config_unfoldTransitionHingeAngle", "bool", "android")
        Resources.getSystem().getBoolean(id)
    }

    override val halfFoldedTimeoutMillis: Int by lazy {
        val id = Resources.getSystem()
            .getIdentifier("config_unfoldTransitionHalfFoldedTimeout", "integer", "android")
        Resources.getSystem().getInteger(id)
    }
}
