package app.lawnchair.compatlib.eleven;

import android.app.IApplicationThread
import android.window.IRemoteTransition
import android.window.RemoteTransition
import app.lawnchair.compatlib.ActivityManagerCompat
import app.lawnchair.compatlib.ActivityOptionsCompat
import app.lawnchair.compatlib.QuickstepCompatFactory
import app.lawnchair.compatlib.RemoteTransitionCompat

open class QuickstepCompatFactoryVR : QuickstepCompatFactory() {

    override fun getActivityManagerCompat(): ActivityManagerCompat {
        return ActivityManagerCompatVR()
    }

    override fun getActivityOptionsCompat(): ActivityOptionsCompat {
        return ActivityOptionsCompatVR()
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
