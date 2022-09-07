package app.lawnchair.root

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.getSystemService

class RootHelperBackend(private val mContext: Context) : IRootHelper.Stub() {
    private val powerManager: PowerManager? get() = mContext.getSystemService()

    override fun goToSleep() {
        powerManager.goToSleep(SystemClock.uptimeMillis())
    }
}
