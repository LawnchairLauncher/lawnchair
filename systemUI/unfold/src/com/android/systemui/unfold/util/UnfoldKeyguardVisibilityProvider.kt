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

import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

interface UnfoldKeyguardVisibilityProvider {
    /**
     * True when the keyguard is visible.
     *
     * Might be [null] when it is not known.
     */
    val isKeyguardVisible: Boolean?
}

/** Used to notify keyguard visibility. */
interface UnfoldKeyguardVisibilityManager {
    /** Sets the delegate. [delegate] should return true when the keyguard is visible. */
    fun setKeyguardVisibleDelegate(delegate: () -> Boolean)
}

/**
 * Keeps a [WeakReference] for the keyguard visibility provider.
 *
 * It is a weak reference because this is in the global scope, while the delegate might be set from
 * another subcomponent (that might have shorter lifespan).
 */
@Singleton
class UnfoldKeyguardVisibilityManagerImpl @Inject constructor() :
    UnfoldKeyguardVisibilityProvider, UnfoldKeyguardVisibilityManager {

    private var delegatedProvider: WeakReference<() -> Boolean?>? = null

    override fun setKeyguardVisibleDelegate(delegate: () -> Boolean) {
        delegatedProvider = WeakReference(delegate)
    }

    override val isKeyguardVisible: Boolean?
        get() = delegatedProvider?.get()?.invoke()
}
