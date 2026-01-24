/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.wm.shell.windowdecor.common.viewhost

import android.content.Context
import android.view.Display
import android.view.SurfaceControl

/** An interface for a supplier of [WindowDecorViewHost]s. */
interface WindowDecorViewHostSupplier<T : WindowDecorViewHost> {
    /** Acquire a [WindowDecorViewHost]. */
    fun acquire(context: Context, display: Display): T

    /**
     * Release a [WindowDecorViewHost] when it is no longer used.
     *
     * @param viewHost the [WindowDecorViewHost] to release
     * @param t a transaction that may be used to remove any underlying backing [SurfaceControl]
     *   that are hosting this [WindowDecorViewHost]. The supplier is not expected to apply the
     *   transaction. It should be applied by the owner of this supplier.
     */
    fun release(viewHost: T, t: SurfaceControl.Transaction)
}
