package app.lawnchair.compatlib;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.IApplicationThread;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;

abstract class RemoteTransitionCompat {

    abstract fun getRemoteTransition(
        @NonNull remoteTransition: IRemoteTransition,
        @Nullable appThread: IApplicationThread?,
        @Nullable debugName: String?
    ): RemoteTransition
}
