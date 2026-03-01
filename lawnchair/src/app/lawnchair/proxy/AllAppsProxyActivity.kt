package app.lawnchair.proxy

import android.app.Activity
import android.os.Bundle
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState

class AllAppsProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcher = Launcher.ACTIVITY_TRACKER.getCreatedActivity<Launcher>()
        launcher?.stateManager?.goToState(LauncherState.ALL_APPS)
        finish()
    }
}
