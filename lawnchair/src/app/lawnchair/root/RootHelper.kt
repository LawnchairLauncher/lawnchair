package app.lawnchair.root

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService

class RootHelper : RootService() {
    override fun onBind(intent: Intent): IBinder {
        return RootHelperBackend(this)
    }
}
