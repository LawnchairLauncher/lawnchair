package app.lawnchair.compatlib.thirteen;

import android.app.IApplicationThread;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.RemoteTransitionCompat;
import app.lawnchair.compatlib.twelve.QuickstepCompatFactoryVS;

open class QuickstepCompatFactoryVT : QuickstepCompatFactoryVS() {

    override fun getActivityManagerCompat(): ActivityManagerCompat {
        return ActivityManagerCompatVT()
    }

    override fun getActivityOptionsCompat(): ActivityOptionsCompat {
        return ActivityOptionsCompatVT()
    }

    override fun getRemoteTransitionCompat(): RemoteTransitionCompat {
        return object : RemoteTransitionCompat() {
            override fun getRemoteTransition(
                remoteTransition: IRemoteTransition,
                appThread: IApplicationThread?,
                debugName: String?
            ): RemoteTransition {
                return RemoteTransition(remoteTransition, appThread)
            }
        }
    }
}
