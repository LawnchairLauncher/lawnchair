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

import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.WindowContainerToken
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.MULTIPLE_SURFACES
import com.android.wm.shell.compatui.letterbox.LetterboxControllerStrategy.LetterboxMode.SINGLE_SURFACE
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/**
 * [LetterboxController] implementation working as coordinator of other [LetterboxController]
 * implementations.
 */
@WMSingleton
class MixedLetterboxController @Inject constructor(
    private val singleSurfaceController: SingleSurfaceLetterboxController,
    private val multipleSurfaceController: MultiSurfaceLetterboxController,
    private val controllerStrategy: LetterboxControllerStrategy,
    private val inputController: LetterboxInputController
) : LetterboxController by singleSurfaceController.append(multipleSurfaceController)
    .append(inputController) {

    override fun createLetterboxSurface(
        key: LetterboxKey,
        transaction: Transaction,
        parentLeash: SurfaceControl,
        token: WindowContainerToken?
    ) {
        when (controllerStrategy.getLetterboxImplementationMode()) {
            SINGLE_SURFACE -> {
                multipleSurfaceController.destroyLetterboxSurface(key, transaction)
                singleSurfaceController.createLetterboxSurface(key, transaction, parentLeash, token)
            }

            MULTIPLE_SURFACES -> {
                singleSurfaceController.destroyLetterboxSurface(key, transaction)
                multipleSurfaceController.createLetterboxSurface(
                    key,
                    transaction,
                    parentLeash,
                    token
                )
            }
        }
        inputController.createLetterboxSurface(key, transaction, parentLeash, token)
    }
}
