package app.lawnchair.compatlib.thirteen;

import android.app.IApplicationThread;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.RemoteTransitionCompat;
import app.lawnchair.compatlib.twelve.QuickstepCompatFactoryVS;

public class QuickstepCompatFactoryVT extends QuickstepCompatFactoryVS {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVT();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVT();
    }

    @NonNull
    @Override
    public RemoteTransitionCompat getRemoteTransitionCompat() {
        return new RemoteTransitionCompat() {
            @Override
            public RemoteTransition getRemoteTransition(@NonNull IRemoteTransition remoteTransition,
                                                        @Nullable IApplicationThread appThread,
                                                        @Nullable String debugName) {
                return new RemoteTransition(remoteTransition, appThread);
            }
        };
    }
}
