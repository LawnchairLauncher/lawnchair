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

package com.android.wm.shell.compatui.letterbox.roundedcorners

import android.content.Context
import android.content.res.Configuration
import android.view.SurfaceControl
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.suppliers.SurfaceBuilderSupplier
import com.android.wm.shell.compatui.letterbox.LetterboxConfiguration
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/** Component responsible for the actual creation of the RoundedCorners surfaces. */
@WMSingleton
class RoundedCornersSurfaceBuilder
@Inject
constructor(
    private val ctx: Context,
    private val syncQueue: SyncTransactionQueue,
    private val surfaceBuilderSupplier: SurfaceBuilderSupplier,
    private val roundedCornersFactory: LetterboxRoundedCornersDrawableFactory,
    private val letterboxConfiguration: LetterboxConfiguration,
) {

    fun create(conf: Configuration, parentLeash: SurfaceControl): RoundedCornersSurface =
        RoundedCornersSurface(
            ctx,
            conf,
            syncQueue,
            parentLeash,
            roundedCornersFactory,
            letterboxConfiguration,
            surfaceBuilderSupplier,
        )
}
