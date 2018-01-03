package ch.deletescape.lawnchair.overlay

import android.content.Context
import ch.deletescape.lawnchair.BuildConfig
import ch.deletescape.lawnchair.Launcher
import ch.deletescape.lawnchair.Utilities
import ch.deletescape.lawnchair.util.PackageManagerHelper

interface ILauncherClient {

    fun onStart()
    fun onStop()
    fun onPause()
    fun onResume()
    fun onDestroy()
    fun onAttachedToWindow()
    fun onDetachedFromWindow()
    fun remove()
    fun openOverlay(animate: Boolean)
    fun hideOverlay(animate: Boolean)
    fun startMove()
    fun endMove()
    fun updateMove(progress: Float)

    val isConnected: Boolean

    companion object {

        fun create(launcher: Launcher): ILauncherClient = if (BuildConfig.ENABLE_LAWNFEED)
            LawnfeedClient(launcher)
        else
            LauncherClientImpl(launcher, true)

        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"

        const val ENABLED = 0
        const val DISABLED_NO_GOOGLE_APP = 1
        const val DISABLED_NO_PROXY_APP = 2
        const val DISABLED_CLIENT_OUTDATED = 3

        fun getEnabledState(context: Context): Int {
            var state = ENABLED
            if (!PackageManagerHelper.isAppEnabled(context.packageManager, GOOGLE_APP_PACKAGE, 0))
                state = state or DISABLED_NO_GOOGLE_APP
            if (BuildConfig.ENABLE_LAWNFEED &&
                    !PackageManagerHelper.isAppEnabled(context.packageManager, LawnfeedClient.PROXY_PACKAGE, 0))
                state = state or DISABLED_NO_PROXY_APP
            if (BuildConfig.ENABLE_LAWNFEED && Utilities.checkOutdatedLawnfeed(context))
                state = state or DISABLED_CLIENT_OUTDATED
            return state
        }
    }
}