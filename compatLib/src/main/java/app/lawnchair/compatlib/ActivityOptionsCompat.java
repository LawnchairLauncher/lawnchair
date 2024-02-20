package app.lawnchair.compatlib;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;

public abstract class ActivityOptionsCompat {
    public abstract ActivityOptions makeCustomAnimation(
            Context context,
            int enterResId,
            int exitResId,
            final Handler callbackHandler,
            final Runnable startedListener,
            final Runnable finishedListener);

    public abstract ActivityOptions makeRemoteAnimation(
            RemoteAnimationAdapter remoteAnimationAdapter,
            Object remoteTransition,
            String debugName);
}
