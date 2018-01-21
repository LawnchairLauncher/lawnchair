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
            // Lawnfeed related checks ONLY for release builds
            if (BuildConfig.ENABLE_LAWNFEED) {
                // Check if Lawnfeed app is installed and enabled
                if (!PackageManagerHelper.isAppEnabled(context.packageManager, LawnfeedClient.PROXY_PACKAGE, 0)) {
                    return DISABLED_NO_PROXY_APP
                }

                // Check if Lawnfeed app is outdated and incompatible with this version
                if (Utilities.checkOutdatedLawnfeed(context)) {
                    return DISABLED_CLIENT_OUTDATED
                }
            }

            // Check if Google app is installed and enabled
            if (!PackageManagerHelper.isAppEnabled(context.packageManager, GOOGLE_APP_PACKAGE, 0)) {
                return DISABLED_NO_GOOGLE_APP
            }

            // Everything is good, continue
            return ENABLED
        }
    }
}