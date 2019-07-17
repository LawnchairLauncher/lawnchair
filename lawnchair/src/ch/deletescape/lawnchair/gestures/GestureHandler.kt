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

package ch.deletescape.lawnchair.gestures

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.View
import com.android.launcher3.R
import org.json.JSONObject

abstract class GestureHandler(val context: Context, val config: JSONObject?) {

    abstract val displayName: String
    open val requiresForeground: Boolean = false
    open val hasConfig = false
    open val configIntent: Intent? = null
    open val isAvailable: Boolean = true
    open val icon: Drawable? = null
    open val iconResource: Intent.ShortcutIconResource by lazy { Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher) }

    abstract fun onGestureTrigger(controller: GestureController, view: View? = null)

    protected open fun saveConfig(config: JSONObject) {

    }

    open fun onConfigResult(data: Intent?) {

    }

    open fun onDestroy() {

    }

    open fun isAvailableForSwipeUp(isSwipeUp: Boolean) = isAvailable

    override fun toString(): String {
        return JSONObject().apply {
            put("class", this@GestureHandler::class.java.name)
            if (hasConfig) {
                val config = JSONObject()
                saveConfig(config)
                put("config", config)
            }
        }.toString()
    }
}

class BlankGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName: String = context.getString(R.string.action_none)

    override fun onGestureTrigger(controller: GestureController, view: View?) {

    }
}

class RunnableGestureHandler(context: Context,
        private val onTrigger: Runnable) : GestureHandler(context, null) {

    override val displayName: String = context.getString(R.string.action_none)

    override fun onGestureTrigger(controller: GestureController, view: View?) {
        onTrigger.run()
    }
}
