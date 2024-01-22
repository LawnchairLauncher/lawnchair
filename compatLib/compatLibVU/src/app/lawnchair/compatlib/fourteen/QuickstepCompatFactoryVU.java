package app.lawnchair.compatlib.fourteen;

import android.app.IApplicationThread;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.RemoteTransitionCompat;
import app.lawnchair.compatlib.thirteen.ActivityOptionsCompatVT;
import app.lawnchair.compatlib.thirteen.QuickstepCompatFactoryVT;

public class QuickstepCompatFactoryVU extends QuickstepCompatFactoryVT {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVU();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVU();
    }

    @NonNull
    @Override
    public RemoteTransitionCompat getRemoteTransitionCompat() {
        return new RemoteTransitionCompat() {
            @Override
            public RemoteTransition getRemoteTransition(@NonNull IRemoteTransition remoteTransition,
                                                        @Nullable IApplicationThread appThread,
                                                        @Nullable String debugName) {
                return new RemoteTransition(remoteTransition, appThread, debugName);
            }
        };
    }
}
