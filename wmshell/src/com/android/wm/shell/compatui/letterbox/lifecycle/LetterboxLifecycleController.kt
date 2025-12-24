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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.view.SurfaceControl

/**
 * Abstract a component that handles the lifecycle of the letterbox surfaces given information
 * encapsulated in a [LetterboxLifecycleEvent].
 */
interface LetterboxLifecycleController {

    /**
     * Describes how [LetterboxLifecycleEvent]s interact with the Letterbox surfaces lifecycle.
     *
     * <p/>
     *
     * @param event The [LetterboxLifecycleEvent] To handle.
     * @param startTransaction The initial [Transaction].
     * @param finishTransaction The final [Transaction].
     */
    fun onLetterboxLifecycleEvent(
        event: LetterboxLifecycleEvent,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    )
}
