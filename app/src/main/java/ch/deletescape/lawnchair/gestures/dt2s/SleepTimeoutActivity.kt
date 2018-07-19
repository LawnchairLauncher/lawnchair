package ch.deletescape.lawnchair.gestures.dt2s

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View

import ch.deletescape.lawnchair.Utilities

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
