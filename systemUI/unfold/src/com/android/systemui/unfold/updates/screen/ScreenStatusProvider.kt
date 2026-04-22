/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.unfold.updates.screen

import com.android.systemui.unfold.updates.screen.ScreenStatusProvider.ScreenListener
import com.android.systemui.unfold.util.CallbackController

interface ScreenStatusProvider : CallbackController<ScreenListener> {

    interface ScreenListener {
        /**
         * Called when the screen is on and ready (windows are drawn and screen blocker is removed)
         */
        fun onScreenTurnedOn()

        /**
         * Called when the screen is starting to be turned off.
         */
        fun onScreenTurningOff()

        /**
         * Called when the screen is starting to be turned on.
         */
        fun onScreenTurningOn()

        /**
         * Called when the screen is already turned on but it happened before the creation
         * of the unfold progress provider, so we won't play the actual animation but we treat
         * the current state of the screen as 'turned on'
         */
        fun markScreenAsTurnedOn()
    }
}
