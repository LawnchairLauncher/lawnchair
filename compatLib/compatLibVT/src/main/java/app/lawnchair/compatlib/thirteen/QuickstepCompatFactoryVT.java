package app.lawnchair.compatlib.thirteen;

import android.window.RemoteTransition;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.RemoteTransitionCompat;
import app.lawnchair.compatlib.twelve.QuickstepCompatFactoryVS;

@RequiresApi(33)
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
        return (remoteTransition, appThread, debugName) ->
                new RemoteTransition(remoteTransition, appThread);
    }
}
