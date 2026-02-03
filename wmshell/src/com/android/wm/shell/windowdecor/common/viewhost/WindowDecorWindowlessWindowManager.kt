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

import android.content.res.Configuration
import android.graphics.Region
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.WindowlessWindowManager

/**
 * A [WindowlessWindowManager] for the window decor caption that allows customizing the touchable
 * region.
 */
class WindowDecorWindowlessWindowManager(
    configuration: Configuration,
    rootSurface: SurfaceControl,
) : WindowlessWindowManager(configuration, rootSurface, /* hostInputTransferToken= */ null) {

    /** Set the view host's touchable region. */
    fun setTouchRegion(viewHost: SurfaceControlViewHost, region: Region?) {
        setTouchRegion(viewHost.windowToken.asBinder(), region)
    }
}
