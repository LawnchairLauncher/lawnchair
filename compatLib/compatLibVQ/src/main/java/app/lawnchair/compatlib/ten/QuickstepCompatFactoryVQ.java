package app.lawnchair.compatlib.ten;

import android.app.IApplicationThread;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.QuickstepCompatFactory;
import app.lawnchair.compatlib.RemoteTransitionCompat;

public class QuickstepCompatFactoryVQ extends QuickstepCompatFactory {
    protected final String TAG = getClass().getCanonicalName();

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVQ();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVQ();
    }

    @NonNull
    @Override
    public RemoteTransitionCompat getRemoteTransitionCompat() {
        return new RemoteTransitionCompat() {
            @Override
            public RemoteTransition getRemoteTransition(
                    @NonNull IRemoteTransition remoteTransition,
                    @Nullable IApplicationThread appThread,
                    @Nullable String debugName) {
                return new RemoteTransition(remoteTransition, appThread, debugName);
            }
        };
    }
}
