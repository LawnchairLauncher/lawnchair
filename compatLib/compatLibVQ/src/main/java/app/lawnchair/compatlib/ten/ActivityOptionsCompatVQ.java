package app.lawnchair.compatlib.ten;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import app.lawnchair.compatlib.ActivityOptionsCompat;

@RequiresApi(29)
public class ActivityOptionsCompatVQ extends ActivityOptionsCompat {
    protected final String TAG = getClass().getCanonicalName();

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
                });
    }

    @Override
    public ActivityOptions makeRemoteAnimation(
            RemoteAnimationAdapter remoteAnimationAdapter,
            Object remoteTransition,
            String debugName) {
        return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter);
    }
}
