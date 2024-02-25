package app.lawnchair.compatlib.thirteen;

import android.app.ActivityOptions;
import android.util.Log;
import android.view.RemoteAnimationAdapter;
import android.window.RemoteTransition;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import app.lawnchair.compatlib.twelve.ActivityOptionsCompatVS;

@RequiresApi(33)
public class ActivityOptionsCompatVT extends ActivityOptionsCompatVS {

    @NonNull
    @Override
    public ActivityOptions makeRemoteAnimation(
            @Nullable RemoteAnimationAdapter remoteAnimationAdapter,
            @Nullable Object remoteTransition,
            @Nullable String debugName) {
        Log.e(TAG, "makeRemoteAnimation: " + debugName);
        return ActivityOptions.makeRemoteAnimation(
                remoteAnimationAdapter, (RemoteTransition) remoteTransition);
    }
}
