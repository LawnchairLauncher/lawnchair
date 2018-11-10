/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.gestures.handlers

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.UserHandle
import android.support.annotation.Keep
import android.widget.Toast
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.gestures.ui.SelectAppActivity
import com.android.launcher3.LauncherState
import ch.deletescape.lawnchair.globalsearch.SearchProviderController
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.widget.WidgetsFullSheet
import org.json.JSONObject

@Keep
open class PlayDespacitoGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_despacito)

    override fun onGestureTrigger(controller: GestureController) {
        val mp = MediaPlayer.create(context, R.raw.despacito)
        mp.start()
    }
}
