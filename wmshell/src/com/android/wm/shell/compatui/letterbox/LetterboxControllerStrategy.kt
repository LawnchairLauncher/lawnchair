/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.MULTIPLE_SURFACES
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.SINGLE_SURFACE
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/**
 * Encapsulate the logic related to the use of a single or multiple surfaces when
 * implementing letterbox in shell.
 */
@WMSingleton
class LetterboxControllerStrategy @Inject constructor(
    private val letterboxConfiguration: LetterboxConfiguration
) {

    // Different letterbox implementation modes.
    enum class LetterboxMode { SINGLE_SURFACE, MULTIPLE_SURFACES }

    @Volatile
    private var currentMode: LetterboxMode = SINGLE_SURFACE

    fun configureLetterboxMode() {
        // TODO(b/377875146): Define criteria for switching between [LetterboxMode]s.
        // At the moment, we use the presence of rounded corners to understand if to use a single
        // surface or multiple surfaces for the letterbox areas. This rule will change when
        // considering transparent activities which won't have rounded corners leading to the
        // [MULTIPLE_SURFACES] option.
        // The chosen strategy will depend on performance considerations,
        // including surface memory usage and the impact of the rounded corners solution.
        currentMode = if (letterboxConfiguration.isLetterboxActivityCornersRounded()) {
            SINGLE_SURFACE
        } else {
            MULTIPLE_SURFACES
        }
    }

    /**
     * @return The specific mode to use for implementing letterboxing for the given [request].
     */
    fun getLetterboxImplementationMode(): LetterboxMode = currentMode
}
