package ch.deletescape.lawnchair.launcherclientproxy

import android.app.Service
import android.content.Intent
import android.os.IBinder

class LauncherClientProxyService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        return getBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        binder?.onUnbind()
        stopSelf()
        return super.onUnbind(intent)
    }

    private fun getBinder(): ProxyImpl {
        if (binder == null) {
            binder = ProxyImpl(applicationContext)
        }
        return binder!!
    }

    private var binder: ProxyImpl? = null
}
