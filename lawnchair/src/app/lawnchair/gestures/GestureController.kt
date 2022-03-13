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
import app.lawnchair.preferences2.PreferenceManager2
import com.patrykmichalik.preferencemanager.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GestureController(private val launcher: LawnchairLauncher) {
    private val preferenceManager = PreferenceManager2.getInstance(launcher)
    private val doubleTapHandler = SleepGestureHandler(launcher)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var dt2s = false

    init {
        preferenceManager.dt2s.onEach(launchIn = coroutineScope) {
            dt2s = it
        }
    }

    fun onDoubleTap() {
        // TODO: proper gesture selection system
        if (dt2s) {
            launcher.lifecycleScope.launch {
                doubleTapHandler.onTrigger(launcher)
            }
        }
    }
}
