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

package com.android.systemui.rotation.impl

import android.content.Context
//import com.android.app.tracing.traceSection
import com.android.internal.view.RotationPolicy
import com.android.internal.view.RotationPolicy.RotationPolicyListener
import com.android.systemui.rotation.RotationPolicyWrapper
import javax.inject.Inject

public class RotationPolicyWrapperImpl @Inject constructor(
    private val context: Context,
) :
        RotationPolicyWrapper {

    override fun setRotationLock(enabled: Boolean, caller: String) {

            RotationPolicy.setRotationLock(context, enabled, caller)

    }

    override fun setRotationLockAtAngle(enabled: Boolean, rotation: Int, caller: String) {
        RotationPolicy.setRotationLockAtAngle(context, enabled, rotation, caller)
    }

    /**
     * Sets screen rotation to [rotation] if the value of [ACCELEROMETER_ROTATION] is false.
     */
    override fun setRotationAtAngleIfAllowed(rotation: Int, caller: String) {

            RotationPolicy.setRotationAtAngleIfAllowed(rotation, caller)

    }

    override fun getRotationLockOrientation(): Int =
        RotationPolicy.getRotationLockOrientation(context)

    override fun isRotationLockToggleVisible(): Boolean =
        RotationPolicy.isRotationLockToggleVisible(context)

    override fun isRotationLocked(): Boolean =
        RotationPolicy.isRotationLocked(context)

    override fun registerRotationPolicyListener(
        listener: RotationPolicyListener,
        userHandle: Int
    ) {
        RotationPolicy.registerRotationPolicyListener(context, listener, userHandle)
    }

    override fun unregisterRotationPolicyListener(listener: RotationPolicyListener) {
        RotationPolicy.unregisterRotationPolicyListener(context, listener)
    }
}
