package app.lawnchair.compatlib;

import android.app.IApplicationThread
import android.window.IRemoteTransition
import android.window.RemoteTransition

abstract class RemoteTransitionCompat {

    abstract fun getRemoteTransition(
        remoteTransition: IRemoteTransition,
        appThread: IApplicationThread?,
        debugName: String?
    ): RemoteTransition
}