package app.lawnchair.nexuslauncher

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import app.lawnchair.FeedBridge
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlay
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlayCallbacks
import com.google.android.libraries.launcherclient.ISerializableScrollCallback
import com.google.android.libraries.launcherclient.LauncherClient
import com.google.android.libraries.launcherclient.LauncherClientCallbacks
import com.google.android.libraries.launcherclient.LauncherClientService
import com.google.android.libraries.launcherclient.StaticInteger
import com.patrykmichalik.opto.core.firstBlocking

/**
 * Implements [LauncherOverlay] and passes all the corresponding events to [LauncherClient],
 * see [LauncherClientService.setClient].
 *
 * Implements [LauncherClientCallbacks] and sends all the corresponding callbacks to [Launcher].
 */
class OverlayCallbackImpl(private val mLauncher: LawnchairLauncher) : LauncherOverlay,
    LauncherClientCallbacks, LauncherOverlayManager, ISerializableScrollCallback {
    private val mClient: LauncherClient
    private var mFlagsChanged = false
    private var mLauncherOverlayCallbacks: LauncherOverlayCallbacks? = null
    private var mWasOverlayAttached = false
    private var mFlags = 0

    init {
        val enableFeed = PreferenceManager2.getInstance(mLauncher).enableFeed.firstBlocking()
        mClient = LauncherClient(
            mLauncher, this, StaticInteger((if (enableFeed) 1 else 0) or 2 or 4 or 8)
        )
    }

    override fun onDeviceProvideChanged() {
        mClient.redraw()
    }

    override fun onAttachedToWindow() {
        mClient.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        mClient.onDetachedFromWindow()
    }

    override fun openOverlay() {
        mClient.showOverlay(true)
    }

    override fun hideOverlay(animate: Boolean) {
        mClient.hideOverlay(animate)
    }

    override fun hideOverlay(duration: Int) {
        mClient.hideOverlay(duration)
    }

    override fun startSearch(config: ByteArray?, extras: Bundle?): Boolean = false

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) {
        mClient.onStart()
    }

    override fun onActivityResumed(activity: Activity) {
        mClient.onResume()
    }

    override fun onActivityPaused(activity: Activity) {
        mClient.onPause()
    }

    override fun onActivityStopped(activity: Activity) {
        mClient.onStop()
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        mClient.onDestroy()
        mClient.mDestroyed = true
    }

    override fun onOverlayScrollChanged(progress: Float) {
        mLauncherOverlayCallbacks?.onScrollChanged(progress)
    }

    override fun onServiceStateChanged(overlayAttached: Boolean, hotwordActive: Boolean) {
        onServiceStateChanged(overlayAttached)
    }

    override fun onServiceStateChanged(overlayAttached: Boolean) {
        if (overlayAttached != mWasOverlayAttached) {
            mWasOverlayAttached = overlayAttached
            mLauncher.setLauncherOverlay(if (overlayAttached) this else null)
        }
    }

    override fun onScrollInteractionBegin() {
        mClient.startScroll()
    }

    override fun onScrollInteractionEnd() {
        mClient.endScroll()
    }

    override fun onScrollChange(progress: Float, rtl: Boolean) {
        mClient.setScroll(progress)
    }

    override fun setOverlayCallbacks(callbacks: LauncherOverlayCallbacks?) {
        mLauncherOverlayCallbacks = callbacks
    }

    override fun setPersistentFlags(flags: Int) {
        val newFlags = flags and (8 or 16)
        if (newFlags != mFlags) {
            mFlagsChanged = true
            mFlags = newFlags
            Utilities.getDevicePrefs(mLauncher).edit().putInt(PREF_PERSIST_FLAGS, newFlags).apply()
        }
    }

    companion object {
        private const val PREF_PERSIST_FLAGS = "pref_persistent_flags"

        fun minusOneAvailable(context: Context): Boolean {
            return FeedBridge.useBridge(context) ||
                context.applicationInfo.flags and
                (ApplicationInfo.FLAG_DEBUGGABLE or ApplicationInfo.FLAG_SYSTEM) != 0
        }
    }
}
