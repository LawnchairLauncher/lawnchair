package app.lawnchair.root

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import app.lawnchair.util.requireSystemService

class RootHelperBackend(private val mContext: Context) : IRootHelper.Stub() {
    private val powerManager: PowerManager get() = mContext.requireSystemService()

    override fun goToSleep() {
        powerManager.goToSleep(SystemClock.uptimeMillis())
    }
}
