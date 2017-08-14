package ch.deletescape.lawnchair.overlay

import android.content.*
import android.content.pm.PackageManager
import android.graphics.Point
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.Window
import android.view.WindowManager
import ch.deletescape.lawnchair.Launcher
import ch.deletescape.lawnchair.Utilities
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback

class LauncherClientImpl(private val launcher: Launcher, targetPackage: String, overlayEnabled: Boolean) : ILauncherClient {
    private var currentCallbacks: OverlayCallbacks? = null
    private var destroyed = false
    private var serviceConnected = false
    private var activityState: Int = 0
    private var overlay: ILauncherOverlay? = null
    private val serviceConnection = OverlayServiceConnection()
    private val serviceConnectionOptions = 3
    private val serviceIntent = getServiceIntent(launcher)
    private var serviceStatus = -1
    private var state = 0
    private var windowAttrs: WindowManager.LayoutParams? = null
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reconnect()
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        filter.addDataScheme("package")
        filter.addDataSchemeSpecificPart(targetPackage, PatternMatcher.PATTERN_LITERAL)
        launcher.registerReceiver(updateReceiver, filter)

        if (version < 1) {
            Pb(launcher)
        }

        reconnect()
    }

    constructor(launcher: Launcher, overlayEnabled: Boolean) : this(launcher, "com.google.android.googlequicksearchbox", overlayEnabled)

    private fun applyWindowToken() {
        if (!isConnected || !Utilities.getPrefs(launcher).showGoogleNowTab) {
            return
        }

        try {
            if (currentCallbacks == null) {
                currentCallbacks = OverlayCallbacks()
            }

            currentCallbacks!!.setClient(this)
            if (version >= 3) {
                val bundle = Bundle()
                bundle.putParcelable("layout_params", windowAttrs)
                bundle.putParcelable("configuration", launcher.resources.configuration)
                bundle.putInt("client_options", serviceConnectionOptions)
                overlay!!.windowAttached2(bundle, currentCallbacks!!)
            } else {
                overlay!!.windowAttached(windowAttrs!!, currentCallbacks!!, serviceConnectionOptions)
            }
            if (version >= 4) {
                overlay!!.setActivityState(activityState)
            } else if (activityState and 2 == 0) {
                overlay!!.onPause()
            } else {
                overlay!!.onResume()
            }
        } catch (ignored: RemoteException) {
        }

    }

    private fun connectSafely(context: Context, conn: ServiceConnection, flags: Int): Boolean {
        try {
            return context.bindService(serviceIntent, conn, flags or Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.e("DrawerOverlayClient", "Unable to connect to overlay service")
            return false
        }

    }

    override val isConnected: Boolean
        get() = overlay != null

    private fun notifyStatusChanged(status: Int) {
        if (serviceStatus == status) {
            return
        }

        serviceStatus = status
    }

    private fun removeClient(removeAppConnection: Boolean) {
        destroyed = true
        if (serviceConnected) {
            launcher.unbindService(serviceConnection)
            serviceConnected = false
        }
        launcher.unregisterReceiver(updateReceiver)

        if (currentCallbacks != null) {
            currentCallbacks!!.clear()
            currentCallbacks = null
        }

        if (removeAppConnection && sApplicationConnection != null) {
            launcher.applicationContext.unbindService(sApplicationConnection!!)
            sApplicationConnection = null
        }
    }

    private fun setWindowAttrs(windowAttrs: WindowManager.LayoutParams?) {
        this.windowAttrs = windowAttrs
        if (this.windowAttrs != null) {
            applyWindowToken()
        } else if (overlay != null) {
            try {
                overlay!!.windowDetached(launcher.isChangingConfigurations)
            } catch (ignored: RemoteException) {
            }

            overlay = null
        }
    }

    override fun endMove() {
        if (!isConnected || !Utilities.getPrefs(launcher).showGoogleNowTab) {
            return
        }

        try {
            overlay!!.endScroll()
        } catch (ignored: RemoteException) {
        }

    }

    override fun hideOverlay(animate: Boolean) {
        if (!isConnected || !Utilities.getPrefs(launcher).showGoogleNowTab) {
            return
        }

        try {
            overlay!!.closeOverlay(if (animate) 1 else 0)
        } catch (ignored: RemoteException) {
        }

    }

    override fun openOverlay(animate: Boolean) {
        if (!isConnected || !Utilities.getPrefs(launcher).showGoogleNowTab) {
            return
        }

        try {
            overlay!!.openOverlay(if (animate) 1 else 0)
        } catch (ignored: RemoteException) {
        }

    }

    override fun onAttachedToWindow() {
        if (!destroyed && Utilities.getPrefs(launcher).showGoogleNowTab) {
            setWindowAttrs(launcher.window.attributes)
        }
    }

    override fun onDestroy() {
        if (Utilities.getPrefs(launcher).showGoogleNowTab) {
            removeClient(!launcher.isChangingConfigurations)
        }
    }

    override fun remove() {
        removeClient(true)
    }

    override fun onDetachedFromWindow() {
        if (!destroyed && Utilities.getPrefs(launcher).showGoogleNowTab) {
            setWindowAttrs(null)
        }
    }

    override fun onStart() {
        if (!destroyed && Utilities.getPrefs(launcher).showGoogleNowTab) {
            activityState = activityState or 1
            if (windowAttrs != null) {
                try {
                    overlay?.setActivityState(activityState)
                } catch (e: RemoteException) {
                }

            }
        }
    }

    override fun onStop() {
        if (!destroyed && Utilities.getPrefs(launcher).showGoogleNowTab) {
            activityState = activityState and -2
            if (windowAttrs != null) {
                try {
                    overlay?.setActivityState(activityState)
                } catch (e: RemoteException) {
                }

            }
        }
    }

    override fun onPause() {
        if (destroyed || !Utilities.getPrefs(launcher).showGoogleNowTab) {
            return
        }
        activityState = activityState and -3
        if (windowAttrs != null) {
            try {
                overlay?.onPause()
            } catch (ignored: RemoteException) {
            }

        }
    }

    override fun onResume() {
        if (destroyed || !Utilities.getPrefs(launcher).showGoogleNowTab) {
            return
        }

        activityState = activityState or 2
        reconnect()
        if (windowAttrs != null) {
            try {
                overlay?.onResume()
            } catch (ignored: RemoteException) {
            }

        }
    }

    fun reconnect() {
        if (destroyed || state != 0 || !Utilities.getPrefs(launcher).showGoogleNowTab) {
            return
        }

        if (sApplicationConnection != null && sApplicationConnection!!.packageName != serviceIntent.`package`) {
            launcher.applicationContext.unbindService(sApplicationConnection!!)
        }

        if (sApplicationConnection == null) {
            sApplicationConnection = AppServiceConnection(serviceIntent.`package`)

            if (!connectSafely(launcher.applicationContext, sApplicationConnection!!, Context.BIND_WAIVE_PRIORITY)) {
                sApplicationConnection = null
            }
        }

        if (sApplicationConnection != null) {
            state = 2

            if (!connectSafely(launcher, serviceConnection, Context.BIND_ADJUST_WITH_ACTIVITY)) {
                state = 0
            } else {
                serviceConnected = true
            }
        }

        if (state == 0) {
            launcher.runOnUiThread { notifyStatusChanged(0) }
        }
    }

    override fun startMove() {
        if (!isConnected || !Utilities.getPrefs(launcher).showGoogleNowTab) {
            return
        }

        try {
            overlay!!.startScroll()
        } catch (ignored: RemoteException) {
        }

    }

    override fun updateMove(progress: Float) {
        if (!isConnected || !Utilities.getPrefs(launcher).showGoogleNowTab) {
            return
        }

        try {
            overlay!!.onScroll(progress)
        } catch (ignored: RemoteException) {
        }

    }


    internal inner class AppServiceConnection(val packageName: String) : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {

        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (name.packageName == packageName) {
                sApplicationConnection = null
            }
        }
    }

    private inner class OverlayCallbacks : ILauncherOverlayCallback.Stub(), Handler.Callback {
        private var mClient: LauncherClientImpl? = null
        private val mUIHandler: Handler
        private var mWindow: Window? = null
        private var mWindowHidden: Boolean = false
        private var mWindowManager: WindowManager? = null
        private var mWindowShift: Int = 0

        init {
            mWindowHidden = false
            mUIHandler = Handler(Looper.getMainLooper(), this)
        }

        private fun hideActivityNonUI(isHidden: Boolean) {
            if (mWindowHidden != isHidden) {
                mWindowHidden = isHidden
            }
        }

        fun clear() {
            mClient = null
            mWindowManager = null
            mWindow = null
        }

        override fun handleMessage(msg: Message): Boolean {
            if (mClient == null) {
                return true
            }

            when (msg.what) {
                3 -> {
                    val attrs = mWindow!!.attributes
                    if (msg.obj as Boolean) {
                        attrs.x = mWindowShift
                        attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    }
                    mWindowManager!!.updateViewLayout(mWindow!!.decorView, attrs)
                    return true
                }
                4 -> {
                    mClient!!.notifyStatusChanged(msg.arg1)
                    return true
                }
                else -> return false
            }
        }

        @Throws(RemoteException::class)
        override fun overlayScrollChanged(progress: Float) {
            mUIHandler.removeMessages(2)
            Message.obtain(mUIHandler, 2, progress).sendToTarget()

            if (progress > 0) {
                hideActivityNonUI(false)
            }

            launcher.runOnUiThread { launcher.workspace.onOverlayScrollChanged(progress) }
        }

        override fun overlayStatusChanged(status: Int) {
            Message.obtain(mUIHandler, 4, status, 0).sendToTarget()
        }

        fun setClient(client: LauncherClientImpl) {
            mClient = client
            mWindowManager = client.launcher.windowManager

            val p = Point()
            mWindowManager!!.defaultDisplay.getRealSize(p)
            mWindowShift = Math.max(p.x, p.y)

            mWindow = client.launcher.window
        }
    }

    private inner class OverlayServiceConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            state = 1
            overlay = ILauncherOverlay.Stub.asInterface(service)
            if (windowAttrs != null) {
                applyWindowToken()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            state = 0
            overlay = null
            notifyStatusChanged(0)
        }
    }

    companion object {
        private var sApplicationConnection: AppServiceConnection? = null

        private var version = -1

        internal fun getServiceIntent(context: Context): Intent {
            val uri = Uri.parse("app://${context.packageName}:${Process.myUid()}").buildUpon()
                    .appendQueryParameter("v", Integer.toString(5))
                    .build()

            return Intent("com.android.launcher3.WINDOW_OVERLAY")
                    .setPackage("com.google.android.googlequicksearchbox")
                    .setData(uri)
        }

        private fun Pb(context: Context) {
            val resolveService = context.packageManager.resolveService(getServiceIntent(context), PackageManager.GET_META_DATA)
            if (resolveService == null || resolveService.serviceInfo.metaData == null) {
                version = 1
            } else {
                version = resolveService.serviceInfo.metaData.getInt("service.api.version", 1)
            }
            Log.v("LauncherClient", "version: $version")
        }
    }
}
