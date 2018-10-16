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

package ch.deletescape.lawnchair.gestures.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.BlankGestureHandler
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.LawnchairShortcutActivity
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R

class RunHandlerActivity : Activity() {
    private val fallback by lazy { BlankGestureHandler(this, null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == LawnchairShortcutActivity.START_ACTION) {
            val handlerString = intent.getStringExtra(LawnchairShortcutActivity.EXTRA_HANDLER)
            if (handlerString != null) {
                val controller = (LauncherAppState.getInstance(this).launcher as? LawnchairLauncher)?.gestureController
                if (controller != null) {
                    GestureController.createGestureHandler(this.applicationContext, handlerString, fallback).onGestureTrigger(controller)
                } else {
                    Toast.makeText(this.applicationContext, R.string.lawnchair_action_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
        finish()
    }
}