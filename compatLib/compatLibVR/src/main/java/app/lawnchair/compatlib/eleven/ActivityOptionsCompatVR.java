package app.lawnchair.compatlib.eleven;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.RemoteAnimationAdapter;
import app.lawnchair.compatlib.ActivityOptionsCompat;

public class ActivityOptionsCompatVR extends ActivityOptionsCompat {
    private static final String TAG = "ActivityOptionsCompatVR";

    @Override
    public ActivityOptions makeCustomAnimation(
            Context context,
            int enterResId,
            int exitResId,
            Runnable callback,
            Handler callbackHandler) {
        return ActivityOptions.makeCustomAnimation(
                context,
                enterResId,
                exitResId,
                callbackHandler,
                new ActivityOptions.OnAnimationStartedListener() {
                    @Override
                    public void onAnimationStarted() {
                        if (callback != null) {
                            callbackHandler.post(callback);
                        }
                    }
                },
                null /* finishedListener */);
    }

    @Override
    public ActivityOptions makeRemoteAnimation(
            RemoteAnimationAdapter remoteAnimationAdapter,
            Object remoteTransition,
            String debugName) {
        Log.e(TAG, "makeRemoteAnimation: " + debugName);
        return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter);
    }
}
