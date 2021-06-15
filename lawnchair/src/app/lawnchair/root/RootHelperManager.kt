package app.lawnchair.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.util.MainThreadInitializedObject
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RootHelperManager(private val context: Context) {
    private val prefs = PreferenceManager.getInstance(context)
    private var rootHelper: IRootHelper? = null
    private val connectionListeners = CopyOnWriteArraySet<Runnable>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            rootHelper = IRootHelper.Stub.asInterface(service)
            connectionListeners.forEach { it.run() }
            connectionListeners.clear()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            rootHelper = null
        }
    }

    @Throws(RootNotAvailableException::class)
    suspend fun getService(): IRootHelper {
        prefs.autoLaunchRoot.set(isAvailable)

        if (!isAvailable) throw RootNotAvailableException()
        if (rootHelper != null) return rootHelper!!

        val intent = Intent(context, RootHelper::class.java)
        RootService.bind(intent, connection)

        return suspendCoroutine {
            connectionListeners.add {
                it.resume(rootHelper!!)
            }
        }
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject(::RootHelperManager)
        val isAvailable by lazy { Shell.rootAccess() }
    }
}
