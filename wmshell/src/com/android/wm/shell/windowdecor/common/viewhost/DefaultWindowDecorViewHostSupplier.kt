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
import com.android.wm.shell.shared.annotations.ShellMainThread
import kotlinx.coroutines.CoroutineScope

/**
 * A supplier of [DefaultWindowDecorViewHost]s. It creates a new one every time one is requested.
 */
class DefaultWindowDecorViewHostSupplier(
    @ShellMainThread private val mainScope: CoroutineScope
) : WindowDecorViewHostSupplier<WindowDecorViewHost> {

    override fun acquire(context: Context, display: Display): WindowDecorViewHost {
        return DefaultWindowDecorViewHost(context, mainScope, display)
    }

    override fun release(viewHost: WindowDecorViewHost, t: SurfaceControl.Transaction) {
        viewHost.release(t)
    }
}
