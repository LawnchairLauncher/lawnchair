package app.lawnchair.compatlib.fourteen;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;
import android.window.RemoteTransition;
import app.lawnchair.compatlib.ActivityOptionsCompat;

public class ActivityOptionsCompatVU extends ActivityOptionsCompat {
    @Override
    public ActivityOptions makeCustomAnimation(
            Context context,
            int enterResId,
            int exitResId,
            Handler callbackHandler,
            Runnable startedListener,
            Runnable finishedListener) {
        return ActivityOptions.makeCustomAnimation(
                context,
                enterResId,
                exitResId,
                0,
                callbackHandler,
                new ActivityOptions.OnAnimationStartedListener() {
                    @Override
                    public void onAnimationStarted(long elapsedRealTime) {
                        if (startedListener != null) {
                            callbackHandler.post(startedListener);
                        }
                    }
                },
                new ActivityOptions.OnAnimationFinishedListener() {
                    @Override
                    public void onAnimationFinished(long elapsedRealTime) {
                        if (finishedListener != null) {
                            callbackHandler.post(finishedListener);
                        }
                    }
                });
    }

    @Override
    public ActivityOptions makeRemoteAnimation(
            RemoteAnimationAdapter remoteAnimationAdapter,
            Object remoteTransition,
            String debugName) {
        return ActivityOptions.makeRemoteAnimation(
                remoteAnimationAdapter, (RemoteTransition) remoteTransition);
    }
}
