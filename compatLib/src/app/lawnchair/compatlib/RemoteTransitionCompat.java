package app.lawnchair.compatlib;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.IApplicationThread;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;

public abstract class RemoteTransitionCompat {


    public abstract RemoteTransition getRemoteTransition(@NonNull IRemoteTransition remoteTransition,
                                  @Nullable IApplicationThread appThread,
                                  @Nullable String debugName);


}
