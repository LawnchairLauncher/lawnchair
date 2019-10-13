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

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.Keep
import android.view.View
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import ch.deletescape.lawnchair.lawnchairApp
import ch.deletescape.lawnchair.root.RootHelperManager
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.quickstep.TouchInteractionService
import org.json.JSONObject

@Keep
open class OpenRecentsGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config),
        VerticalSwipeGestureHandler, StateChangeGestureHandler {

    override val displayName: String = context.getString(R.string.action_open_recents)
    override val isAvailable: Boolean
        get() = TouchInteractionService.isConnected()
    override val iconResource: Intent.ShortcutIconResource by lazy { Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_lawnstep) }

    override fun onGestureTrigger(controller: GestureController, view: View?) {
        controller.launcher.stateManager.goToState(LauncherState.OVERVIEW)
    }

    override fun isAvailableForSwipeUp(isSwipeUp: Boolean) = !isSwipeUp

    override fun getTargetState(): LauncherState {
        return LauncherState.OVERVIEW
    }
}

@Keep
@TargetApi(Build.VERSION_CODES.P)
open class PressBackGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName: String = context.getString(R.string.action_press_back)

    override fun onGestureTrigger(controller: GestureController, view: View?) {
        if (RootHelperManager.isAvailable) {
            val time = SystemClock.uptimeMillis()
            RootHelperManager.getInstance(context).run {
                it.sendKeyEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0, time, time)
                it.sendKeyEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, 0, time, time)
            }
        } else {
            context.lawnchairApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    override fun isAvailableForSwipeUp(isSwipeUp: Boolean) = isSwipeUp
}
