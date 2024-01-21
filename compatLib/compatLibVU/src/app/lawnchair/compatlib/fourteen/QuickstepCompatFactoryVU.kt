package app.lawnchair.compatlib.fourteen;

import android.app.IApplicationThread
import android.window.IRemoteTransition
import android.window.RemoteTransition
import app.lawnchair.compatlib.ActivityManagerCompat
import app.lawnchair.compatlib.ActivityOptionsCompat
import app.lawnchair.compatlib.RemoteTransitionCompat
import app.lawnchair.compatlib.thirteen.QuickstepCompatFactoryVT

class QuickstepCompatFactoryVU : QuickstepCompatFactoryVT() {

    override fun getActivityManagerCompat(): ActivityManagerCompat {
        return ActivityManagerCompatVU()
    }

    override fun getActivityOptionsCompat(): ActivityOptionsCompat {
        return ActivityOptionsCompatVU()
    }

    override fun getRemoteTransitionCompat(): RemoteTransitionCompat {
        return object : RemoteTransitionCompat() {
            override fun getRemoteTransition(
                remoteTransition: IRemoteTransition,
                appThread: IApplicationThread?,
                debugName: String?
            ): RemoteTransition {
                return RemoteTransition(remoteTransition, appThread, debugName)
            }
        }
    }
}
