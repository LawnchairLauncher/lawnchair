package app.lawnchair.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.MainThreadInitializedObject
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

class RootHelperManager(private val context: Context) {
    private val scope = MainScope() + CoroutineName("RootHelperManager")
    private val prefs = PreferenceManager.getInstance(context)
    private val isAvailableDeferred by lazy {
        scope.async(Dispatchers.IO) {
            Shell.rootAccess()
        }
    }
    private var rootHelperDeferred: Deferred<IRootHelper>? = null

    suspend fun isAvailable() = isAvailableDeferred.await()

    @Throws(RootNotAvailableException::class)
    suspend fun getService(): IRootHelper {
        prefs.autoLaunchRoot.set(isAvailable())

        if (!isAvailable()) throw RootNotAvailableException()

        if (rootHelperDeferred == null) {
            rootHelperDeferred = scope.async {
                bindImpl { rootHelperDeferred = null }
            }
        }
        return rootHelperDeferred!!.await()
    }

    private suspend fun bindImpl(onDisconnected: () -> Unit): IRootHelper {
        return withContext(Dispatchers.IO) {
            val intent = Intent(context, RootHelper::class.java)
            suspendCoroutine {
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        it.resume(IRootHelper.Stub.asInterface(service))
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        onDisconnected()
                    }
                }
                RootService.bind(intent, connection)
            }
        }
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject(::RootHelperManager)
    }
}
