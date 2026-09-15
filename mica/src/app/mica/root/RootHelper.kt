package app.mica.root

import android.content.Intent
import com.topjohnwu.superuser.ipc.RootService

class RootHelper : RootService() {
    override fun onBind(intent: Intent) = RootHelperBackend(this)
}
