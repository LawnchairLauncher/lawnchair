package app.lawnchair.compatlib.twelve;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;

import app.lawnchair.compatlib.ActivityOptionsCompat;

public class ActivityOptionsCompatVS extends ActivityOptionsCompat {
    @Override
    public ActivityOptions makeCustomAnimation(Context context, int enterResId, int exitResId, Runnable callback, Handler callbackHandler) {
        return ActivityOptions.makeCustomTaskAnimation(context, enterResId, exitResId,
                callbackHandler,
                new ActivityOptions.OnAnimationStartedListener() {
                    @Override
                    public void onAnimationStarted() {
                        if (callback != null) {
                            callbackHandler.post(callback);
                        }
                    }
                }, null /* finishedListener */);
    }

    @Override
    public ActivityOptions makeRemoteAnimation(RemoteAnimationAdapter remoteAnimationAdapter, Object remoteTransition, String debugName) {
        return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter);
    }
}
