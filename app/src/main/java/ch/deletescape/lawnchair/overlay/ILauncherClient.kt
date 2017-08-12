package ch.deletescape.lawnchair.overlay

import ch.deletescape.lawnchair.BuildConfig
import ch.deletescape.lawnchair.Launcher

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

        fun create(launcher: Launcher): ILauncherClient = if (BuildConfig.DEBUG)
            LauncherClientImpl(launcher, true)
        else
            ProxiedLauncherClient(launcher)
    }
}