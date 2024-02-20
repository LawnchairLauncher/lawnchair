package app.lawnchair.compatlib.twelve;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.RemoteAnimationAdapter;
import app.lawnchair.compatlib.ActivityOptionsCompat;

public class ActivityOptionsCompatVS extends ActivityOptionsCompat {
    private static final String TAG = "ActivityOptionsCompatVS";

    @Override
    public ActivityOptions makeCustomAnimation(
            Context context,
            int enterResId,
            int exitResId,
            Handler callbackHandler,
            Runnable startedListener,
            Runnable finishedListener) {
        return ActivityOptions.makeCustomTaskAnimation(
                context,
                enterResId,
                exitResId,
                callbackHandler,
                new ActivityOptions.OnAnimationStartedListener() {
                    @Override
                    public void onAnimationStarted() {
                        if (startedListener != null) {
                            startedListener.run();
                        }
                    }
                },
                new ActivityOptions.OnAnimationFinishedListener() {
                    @Override
                    public void onAnimationFinished() {
                        if (finishedListener != null) {
                            finishedListener.run();
                        }
                    }
                });
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
