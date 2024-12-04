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

package com.android.wm.shell.windowdecor.additionalviewcontainer

import android.view.SurfaceControl
import android.view.View
import com.android.wm.shell.windowdecor.WindowDecoration

/**
 * Class for additional view containers associated with a [WindowDecoration].
 */
abstract class AdditionalViewContainer internal constructor(
) {
    abstract val view: View?

    /** Release the view associated with this container and perform needed cleanup. */
    abstract fun releaseView()

    /** Reposition the view container using provided coordinates. */
    abstract fun setPosition(t: SurfaceControl.Transaction, x: Float, y: Float)
}
