package com.google.android.libraries.launcherclient

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

class LauncherClient(private val mLauncher: Launcher, targetPackage: String, overlayEnabled: Boolean) {
    private var mCurrentCallbacks: OverlayCallbacks? = null
    private var mDestroyed: Boolean = false
    private var mIsServiceConnected: Boolean = false
    private var activityState: Int = 0
    private var mOverlay: ILauncherOverlay? = null
    private val mServiceConnection: OverlayServiceConnection
    private val mServiceConnectionOptions: Int
    private val mServiceIntent: Intent
    private var mServiceStatus: Int = 0
    private var mState: Int = 0
    private val mUpdateReceiver: BroadcastReceiver
    private var mWindowAttrs: WindowManager.LayoutParams? = null

    init {
        mUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                reconnect()
            }
        }
        mDestroyed = false
        mIsServiceConnected = false
        mServiceStatus = -1
        mServiceIntent = LauncherClient.getServiceIntent(mLauncher)
        mState = 0
        mServiceConnection = OverlayServiceConnection()
        mServiceConnectionOptions = if (overlayEnabled) 3 else 2

        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        filter.addDataScheme("package")
        filter.addDataSchemeSpecificPart(targetPackage, PatternMatcher.PATTERN_LITERAL)
        mLauncher.registerReceiver(mUpdateReceiver, filter)

        if (version < 1) {
            LauncherClient.Pb(mLauncher)
        }

        reconnect()
    }

    constructor(launcher: Launcher, overlayEnabled: Boolean) : this(launcher, "com.google.android.googlequicksearchbox", overlayEnabled)

    private fun applyWindowToken() {
        if (!isConnected || !Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            return
        }

        try {
            if (mCurrentCallbacks == null) {
                mCurrentCallbacks = OverlayCallbacks()
            }

            mCurrentCallbacks!!.setClient(this)
            if (version >= 3) {
                val bundle = Bundle()
                bundle.putParcelable("layout_params", mWindowAttrs)
                bundle.putParcelable("configuration", mLauncher.resources.configuration)
                bundle.putInt("client_options", mServiceConnectionOptions)
                mOverlay!!.windowAttached2(bundle, mCurrentCallbacks!!)
            } else {
                mOverlay!!.windowAttached(mWindowAttrs!!, mCurrentCallbacks!!, mServiceConnectionOptions)
            }
            if (version >= 4) {
                mOverlay!!.setActivityState(activityState)
            } else if (activityState and 2 == 0) {
                mOverlay!!.onPause()
            } else {
                mOverlay!!.onResume()
            }
        } catch (ignored: RemoteException) {
        }

    }

    private fun connectSafely(context: Context, conn: ServiceConnection, flags: Int): Boolean {
        try {
            return context.bindService(mServiceIntent, conn, flags or Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.e("DrawerOverlayClient", "Unable to connect to overlay service")
            return false
        }

    }

    val isConnected: Boolean
        get() = mOverlay != null

    private fun notifyStatusChanged(status: Int) {
        if (mServiceStatus == status) {
            return
        }

        mServiceStatus = status
    }

    private fun removeClient(removeAppConnection: Boolean) {
        mDestroyed = true
        if (mIsServiceConnected) {
            mLauncher.unbindService(mServiceConnection)
            mIsServiceConnected = false
        }
        mLauncher.unregisterReceiver(mUpdateReceiver)

        if (mCurrentCallbacks != null) {
            mCurrentCallbacks!!.clear()
            mCurrentCallbacks = null
        }

        if (removeAppConnection && sApplicationConnection != null) {
            mLauncher.applicationContext.unbindService(sApplicationConnection!!)
            sApplicationConnection = null
        }
    }

    private fun setWindowAttrs(windowAttrs: WindowManager.LayoutParams?) {
        mWindowAttrs = windowAttrs
        if (mWindowAttrs != null) {
            applyWindowToken()
        } else if (mOverlay != null) {
            try {
                mOverlay!!.windowDetached(mLauncher.isChangingConfigurations)
            } catch (ignored: RemoteException) {
            }

            mOverlay = null
        }
    }

    fun endMove() {
        if (!isConnected || !Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            return
        }

        try {
            mOverlay!!.endScroll()
        } catch (ignored: RemoteException) {
        }

    }

    fun hideOverlay(animate: Boolean) {
        if (!isConnected || !Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            return
        }

        try {
            mOverlay!!.closeOverlay(if (animate) 1 else 0)
        } catch (ignored: RemoteException) {
        }

    }

    fun openOverlay(animate: Boolean) {
        if (!isConnected || !Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            return
        }

        try {
            mOverlay!!.openOverlay(if (animate) 1 else 0)
        } catch (ignored: RemoteException) {
        }

    }

    fun onAttachedToWindow() {
        if (!mDestroyed && Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            setWindowAttrs(mLauncher.window.attributes)
        }
    }

    fun onDestroy() {
        if (Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            removeClient(!mLauncher.isChangingConfigurations)
        }
    }

    fun remove() {
        removeClient(true)
    }

    fun onDetachedFromWindow() {
        if (!mDestroyed && Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            setWindowAttrs(null)
        }
    }

    fun onStart() {
        if (!mDestroyed && Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            activityState = activityState or 1
            if (mOverlay != null && mWindowAttrs != null) {
                try {
                    mOverlay!!.setActivityState(activityState)
                } catch (e: RemoteException) {
                }

            }
        }
    }

    fun onStop() {
        if (!mDestroyed && Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            activityState = activityState and -2
            if (mOverlay != null && mWindowAttrs != null) {
                try {
                    mOverlay!!.setActivityState(activityState)
                } catch (e: RemoteException) {
                }

            }
        }
    }

    fun onPause() {
        if (mDestroyed || !Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            return
        }
        activityState = activityState and -3
        if (mOverlay != null && mWindowAttrs != null) {
            try {
                mOverlay!!.onPause()
            } catch (ignored: RemoteException) {
            }

        }
    }

    fun onResume() {
        if (mDestroyed || !Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            return
        }

        activityState = activityState or 2
        reconnect()
        if (mOverlay != null && mWindowAttrs != null) {
            try {
                mOverlay!!.onResume()
            } catch (ignored: RemoteException) {
            }

        }
    }

    fun reconnect() {
        if (mDestroyed || mState != 0 || !Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            return
        }

        if (sApplicationConnection != null && sApplicationConnection!!.packageName != mServiceIntent.`package`) {
            mLauncher.applicationContext.unbindService(sApplicationConnection!!)
        }

        if (sApplicationConnection == null) {
            sApplicationConnection = AppServiceConnection(mServiceIntent.`package`)

            if (!connectSafely(mLauncher.applicationContext, sApplicationConnection!!, Context.BIND_WAIVE_PRIORITY)) {
                sApplicationConnection = null
            }
        }

        if (sApplicationConnection != null) {
            mState = 2

            if (!connectSafely(mLauncher, mServiceConnection, Context.BIND_ADJUST_WITH_ACTIVITY)) {
                mState = 0
            } else {
                mIsServiceConnected = true
            }
        }

        if (mState == 0) {
            mLauncher.runOnUiThread { notifyStatusChanged(0) }
        }
    }

    fun startMove() {
        if (!isConnected || !Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            return
        }

        try {
            mOverlay!!.startScroll()
        } catch (ignored: RemoteException) {
        }

    }

    fun updateMove(progressX: Float) {
        if (!isConnected || !Utilities.getPrefs(mLauncher).showGoogleNowTab()) {
            return
        }

        try {
            mOverlay!!.onScroll(progressX)
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
        private var mClient: LauncherClient? = null
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

            mLauncher.runOnUiThread { mLauncher.workspace.onOverlayScrollChanged(progress) }
        }

        override fun overlayStatusChanged(status: Int) {
            Message.obtain(mUIHandler, 4, status, 0).sendToTarget()
        }

        fun setClient(client: LauncherClient) {
            mClient = client
            mWindowManager = client.mLauncher.windowManager

            val p = Point()
            mWindowManager!!.defaultDisplay.getRealSize(p)
            mWindowShift = Math.max(p.x, p.y)

            mWindow = client.mLauncher.window
        }
    }

    private inner class OverlayServiceConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mState = 1
            mOverlay = ILauncherOverlay.Stub.asInterface(service)
            if (mWindowAttrs != null) {
                applyWindowToken()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mState = 0
            mOverlay = null
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
            val resolveService = context.packageManager.resolveService(LauncherClient.getServiceIntent(context), PackageManager.GET_META_DATA)
            if (resolveService == null || resolveService.serviceInfo.metaData == null) {
                version = 1
            } else {
                version = resolveService.serviceInfo.metaData.getInt("service.api.version", 1)
            }
            Log.v("LauncherClient", "version: ${version}")
        }
    }
}
