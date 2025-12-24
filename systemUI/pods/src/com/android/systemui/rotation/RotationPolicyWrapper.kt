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

package com.android.systemui.rotation

import com.android.internal.view.RotationPolicy
import com.android.internal.view.RotationPolicy.RotationPolicyListener

/**
 * Testable wrapper interface around RotationPolicy {link com.android.internal.view.RotationPolicy}
 */
public interface RotationPolicyWrapper {
    public fun setRotationLock(enabled: Boolean, caller: String)
    public fun setRotationLockAtAngle(enabled: Boolean, rotation: Int, caller: String)
    public fun setRotationAtAngleIfAllowed(rotation: Int, caller: String)
    public fun getRotationLockOrientation(): Int
    public fun isRotationLockToggleVisible(): Boolean
    public fun isRotationLocked(): Boolean
    public fun registerRotationPolicyListener(listener: RotationPolicyListener, userHandle: Int)
    public fun unregisterRotationPolicyListener(listener: RotationPolicyListener)
}