package app.lawnchair.compatlib.thirteen;

import android.app.ActivityOptions;
import android.util.Log;
import android.view.RemoteAnimationAdapter;
import android.window.RemoteTransition;
import androidx.annotation.RequiresApi;
import app.lawnchair.compatlib.twelve.ActivityOptionsCompatVS;

@RequiresApi(33)
public class ActivityOptionsCompatVT extends ActivityOptionsCompatVS {

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
