package app.lawnchair.compatlib.thirteen;

import android.app.ActivityOptions;
import android.util.Log;
import android.view.RemoteAnimationAdapter;
import android.window.RemoteTransition;
import app.lawnchair.compatlib.twelve.ActivityOptionsCompatVS;

public class ActivityOptionsCompatVT extends ActivityOptionsCompatVS {

    private static final String TAG = "ActivityOptionsCompatVT";

    @Override
    public ActivityOptions makeRemoteAnimation(
            RemoteAnimationAdapter remoteAnimationAdapter,
            Object remoteTransition,
            String debugName) {
        Log.e(TAG, "makeRemoteAnimation: " + debugName);
        return ActivityOptions.makeRemoteAnimation(
                remoteAnimationAdapter, (RemoteTransition) remoteTransition);
    }
}
