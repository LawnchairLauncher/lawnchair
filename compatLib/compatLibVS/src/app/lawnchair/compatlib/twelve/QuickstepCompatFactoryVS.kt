package app.lawnchair.compatlib.twelve;

import android.app.IApplicationThread
import android.window.IRemoteTransition
import android.window.RemoteTransition
import app.lawnchair.compatlib.ActivityManagerCompat
import app.lawnchair.compatlib.ActivityOptionsCompat
import app.lawnchair.compatlib.QuickstepCompatFactory
import app.lawnchair.compatlib.RemoteTransitionCompat
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException

open class QuickstepCompatFactoryVS : QuickstepCompatFactory() {

    override fun getActivityManagerCompat(): ActivityManagerCompat {
        return ActivityManagerCompatVS()
    }

    override fun getActivityOptionsCompat(): ActivityOptionsCompat {
        return ActivityOptionsCompatVS()
    }

    override fun getRemoteTransitionCompat(): RemoteTransitionCompat {
        return object : RemoteTransitionCompat() {
            override fun getRemoteTransition(
                remoteTransition: IRemoteTransition,
                appThread: IApplicationThread?,
                debugName: String?
            ): RemoteTransition {
                return createRemoteTransition(remoteTransition, appThread, debugName)
            }
        }
    }

    // TODO remove this as it causing glitches on first launch opening/closing app
    private fun createRemoteTransition(
        remoteTransition: IRemoteTransition,
        appThread: IApplicationThread?,
        debugName: String?
    ): RemoteTransition {
        try {
            val remoteTransitionClass = Class.forName("android.window.RemoteTransition")
            val constructor: Constructor<*> =
                remoteTransitionClass.getConstructor(IRemoteTransition::class.java, IApplicationThread::class.java, String::class.java)
            return constructor.newInstance(remoteTransition, appThread) as RemoteTransition
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Error creating RemoteTransitionCompat$debugName", e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Error creating RemoteTransitionCompat$debugName", e)
        } catch (e: InstantiationException) {
            throw RuntimeException("Error creating RemoteTransitionCompat$debugName", e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Error creating RemoteTransitionCompat$debugName", e)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Error creating RemoteTransitionCompat$debugName", e)
        }
    }
}
