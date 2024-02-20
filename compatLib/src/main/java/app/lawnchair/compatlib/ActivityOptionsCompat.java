package app.lawnchair.compatlib;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class ActivityOptionsCompat {
    public abstract ActivityOptions makeCustomAnimation(
            Context context,
            int enterResId,
            int exitResId,
            @NonNull final Handler callbackHandler,
            @Nullable final Runnable startedListener,
            @Nullable final Runnable finishedListener);

    public abstract ActivityOptions makeRemoteAnimation(
            RemoteAnimationAdapter remoteAnimationAdapter,
            Object remoteTransition,
            String debugName);
}
