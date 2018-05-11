package ch.deletescape.lawnchair.lawnfeed

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import ch.deletescape.lawnchair.launcherclient.ILauncherClientProxy
import ch.deletescape.lawnchair.launcherclient.ILauncherClientProxyCallback
import ch.deletescape.lawnchair.launcherclient.LauncherClientProxyConnection
import ch.deletescape.lawnchair.launcherclient.WindowLayoutParams
import ch.deletescape.lawnchair.lawnfeed.updater.Updater
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback

class ProxyImpl(val context: Context) : ILauncherClientProxy.Stub() {
    private lateinit var proxyCallback: ILauncherClientProxyCallback
    private var overlayCallbacks = OverlayCallbacks()
    private var destroyed = false
    private var serviceConnected: Boolean = false
    private var overlay: ILauncherOverlay? = null
    private var allowed = false
    private val serviceConnection = OverlayServiceConnection()
    private val serviceIntent = ProxyImpl.getServiceIntent(context)
    private var serviceStatus: Int = 0

    override fun reconnect(): Int {
        enforcePermission()
        try {
            if (destroyed || serviceStatus != 0)
                return serviceStatus

            if (sApplicationConnection != null && sApplicationConnection?.packageName != serviceIntent.`package`)
                context.unbindService(sApplicationConnection)

            if (sApplicationConnection == null) {
                sApplicationConnection = AppServiceConnection(serviceIntent.`package`)

                if (!connectSafely(context, sApplicationConnection!!, Context.BIND_WAIVE_PRIORITY)) {
                    sApplicationConnection = null
                }
            }

            if (sApplicationConnection != null) {
                serviceStatus = LauncherClientProxyConnection.SERVICE_CONNECTING

                if (!connectSafely(context, serviceConnection, Context.BIND_ADJUST_WITH_ACTIVITY)) {
                    serviceStatus = LauncherClientProxyConnection.SERVICE_DISCONNECTED
                } else {
                    serviceConnected = true
                }
            }

            if (serviceStatus == 0) {
                proxyCallback.overlayStatusChanged(0)
            }
        } catch (e: Exception) {
            Log.d(TAG, "error reconnecting", e)
        }

        return serviceStatus
    }

    private fun connectSafely(context: Context, conn: ServiceConnection, flags: Int): Boolean {
        try {
            return context.bindService(serviceIntent, conn, flags or Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.e("DrawerOverlayClient", "Unable to connect to overlay service")
            return false
        }

    }

    override fun closeOverlay(options: Int) {
        enforcePermission()
        overlay?.closeOverlay(options)
    }

    override fun endScroll() {
        enforcePermission()
        overlay?.endScroll()
    }

    override fun onPause() {
        enforcePermission()
        overlay?.onPause()
    }

    override fun onResume() {
        enforcePermission()
        overlay?.onResume()
    }

    override fun onScroll(progress: Float) {
        enforcePermission()
        overlay?.onScroll(progress)
    }

    override fun openOverlay(options: Int) {
        enforcePermission()
        overlay?.openOverlay(options)
    }

    override fun startScroll() {
        enforcePermission()
        overlay?.startScroll()
    }

    override fun windowAttached(attrs: WindowLayoutParams, options: Int) {
        enforcePermission()
        overlay?.windowAttached(attrs.layoutParams, overlayCallbacks, options)
    }

    override fun windowAttached2(bundle: Bundle) {
        enforcePermission()
        overlay?.windowAttached2(bundle, overlayCallbacks)
    }

    override fun setActivityState(activityState: Int) {
        enforcePermission()
        overlay?.setActivityState(activityState)
    }

    override fun windowDetached(isChangingConfigurations: Boolean) {
        enforcePermission()
        overlay?.windowDetached(isChangingConfigurations)
    }

    override fun onQsbClick(intent: Intent?) {
        enforcePermission()
        context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {

            @Suppress("NAME_SHADOWING")
            override fun onReceive(context: Context?, intent: Intent?) {
                proxyCallback.onQsbResult(resultCode)
            }
        }, null, 0, null, null)
    }

    override fun init(callback: ILauncherClientProxyCallback): Int {
        allowed = "ch.deletescape.lawnchair.plah" == callingPackage || "ch.deletescape.lawnchair" == callingPackage
        enforcePermission()
        proxyCallback = callback
        Updater.checkUpdate(context)
        ProxyImpl.getVersion(context)
        return version
    }

    private val callingPackage get() = context.packageManager.getNameForUid(Binder.getCallingUid())

    fun enforcePermission() {
        if (!allowed)
            throw SecurityException("$callingPackage is not allowed to call this service")
    }

    inner class OverlayCallbacks : ILauncherOverlayCallback.Stub() {
        override fun overlayScrollChanged(progress: Float) {
            if (!destroyed)
                proxyCallback.overlayScrollChanged(progress)
        }

        override fun overlayStatusChanged(status: Int) {
            if (!destroyed)
                proxyCallback.overlayStatusChanged(status)
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

    private inner class OverlayServiceConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            overlay = ILauncherOverlay.Stub.asInterface(service)
            serviceStatus = LauncherClientProxyConnection.SERVICE_CONNECTED
            proxyCallback.onServiceConnected()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            overlay = null
            serviceStatus = LauncherClientProxyConnection.SERVICE_DISCONNECTED
            proxyCallback.onServiceDisconnected()
        }
    }

    companion object {
        const val TAG = "ProxyImpl"

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

        private fun getVersion(context: Context) {
            val resolveService = context.packageManager.resolveService(getServiceIntent(context), PackageManager.GET_META_DATA)
            version = resolveService?.serviceInfo?.metaData?.getInt("service.api.version", 1) ?: 1
            Log.v("LauncherClient", "version: $version")
        }
    }

    fun onUnbind() {
        Log.d(TAG, "onUnbind")
        destroyed = true
        if (serviceConnected)
            context.unbindService(serviceConnection)
        if (sApplicationConnection != null)
            context.unbindService(sApplicationConnection)
        sApplicationConnection = null
    }
}