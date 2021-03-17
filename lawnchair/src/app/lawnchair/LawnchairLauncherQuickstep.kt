package app.lawnchair

import android.content.Context
import android.content.ContextWrapper
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.systemui.plugins.shared.LauncherOverlayManager
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.util.restartLauncher
import com.android.launcher3.LauncherAppState

open class LawnchairLauncherQuickstep : QuickstepLauncher() {
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
        fun getLauncher(context: Context): LawnchairLauncherQuickstep {
            return context as? LawnchairLauncherQuickstep
                    ?: (context as ContextWrapper).baseContext as? LawnchairLauncherQuickstep
                    ?: LauncherAppState.getInstance(context).launcher as LawnchairLauncherQuickstep
        }
    }
}