/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.gestures

import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.handlers.SleepGestureHandler
import app.lawnchair.preferences.PreferenceManager
import kotlinx.coroutines.launch

class GestureController(private val launcher: LawnchairLauncher) {
    private val prefs = PreferenceManager.getInstance(launcher)
    private val doubleTapHandler = SleepGestureHandler(launcher)

    fun onDoubleTap() {
        // TODO: proper gesture selection system
        if (prefs.workspaceDt2s.get()) {
            launcher.lifecycleScope.launch {
                doubleTapHandler.onTrigger(launcher)
            }
        }
    }
}
