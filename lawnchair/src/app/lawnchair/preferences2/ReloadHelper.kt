/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.preferences2

import android.content.Context
import androidx.annotation.Discouraged
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.quickstep.TouchInteractionService
import com.android.quickstep.util.TISBindHelper

class ReloadHelper(private val context: Context) {

    private val idp: InvariantDeviceProfile
        get() = InvariantDeviceProfile.INSTANCE.get(context)
    private var tis: TouchInteractionService.TISBinder? = null
    private val tisBinder = TISBindHelper(context) { tis = it }

    fun reloadGrid() {
        idp.onPreferencesChanged(context)
    }

    fun recreate() {
        LawnchairLauncher.instance?.recreateIfNotScheduled()
    }

    fun restart() {
        reloadGrid()
        recreate()
    }

    @Discouraged("This literally reload the models like forceRefresh because the old code has been removed in L3")
    fun reloadIcons() {
        LauncherAppState.INSTANCE.get(context).model.forceReload()
    }

    fun reloadTaskbar() {
        tisBinder.runOnBindToTouchInteractionService {
            tis?.taskbarManager?.recreateTaskbars()
        }
    }
}
