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

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import com.android.launcher3.Utilities

class SleepTimeoutActivity : Activity() {

    private val timeout by lazy { Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000) }
    private val stayOnWhilePluggedIn by lazy { Settings.System.getInt(contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load original values
        timeout
        stayOnWhilePluggedIn

        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        putSettings(0, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!hasFocus) {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        putSettings(timeout, stayOnWhilePluggedIn)
    }

    override fun onBackPressed() {

    }

    private fun putSettings(timeout: Int, stayOnWhilePluggedIn: Int) {
        if (Utilities.ATLEAST_MARSHMALLOW && !Settings.System.canWrite(this)) return
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeout)
        Settings.System.putInt(contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, stayOnWhilePluggedIn)
        Log.d("SleepTimeoutActivity", "Screen timeout settings set to $timeout $stayOnWhilePluggedIn")
    }
}
