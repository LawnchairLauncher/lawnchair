package app.lawnchair

import android.content.Context
import android.content.ContextWrapper
import com.android.launcher3.Launcher
import com.android.systemui.plugins.shared.LauncherOverlayManager
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.util.restartLauncher
import com.android.launcher3.LauncherAppState

open class LawnchairLauncher : Launcher() {
    private var paused = false

    override fun getDefaultOverlay(): LauncherOverlayManager {
        return OverlayCallbackImpl(this)
    }

    override fun onResume() {
        super.onResume()

        restartIfPending()

        paused = false
    }

    override fun onPause() {
        super.onPause()

        paused = true
    }

    open fun restartIfPending() {
        if (sRestart) {
            lawnchairApp.restart(false)
        }
    }

    fun scheduleRestart() {
        if (paused) {
            sRestart = true
        } else {
            restartLauncher(this)
        }
    }

    companion object {
        var sRestart = false
        @JvmStatic
        fun getLauncher(context: Context): LawnchairLauncher {
            return context as? LawnchairLauncher
                    ?: (context as ContextWrapper).baseContext as? LawnchairLauncher
                    ?: LauncherAppState.getInstance(context).launcher as LawnchairLauncher
        }
    }
}