package app.lawnchair.compatlib.eleven;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.RemoteAnimationAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.lawnchair.compatlib.ActivityOptionsCompat;

public class ActivityOptionsCompatVR extends ActivityOptionsCompat {
    private static final String TAG = "ActivityOptionsCompatVR";

    @Override
    public ActivityOptions makeCustomAnimation(
            Context context,
            int enterResId,
            int exitResId,
            @NonNull final Handler callbackHandler,
            @Nullable final Runnable startedListener,
            @Nullable final Runnable finishedListener) {
        return ActivityOptions.makeCustomAnimation(
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
