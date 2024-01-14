package app.lawnchair.compatlib;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;
import android.window.RemoteTransition;

abstract public class ActivityOptionsCompat {
    public abstract ActivityOptions makeCustomAnimation(Context context, int enterResId,
                                                        int exitResId, final Runnable callback, final Handler callbackHandler);

    public static ActivityOptions makeRemoteAnimation(
            RemoteAnimationAdapter remoteAnimationAdapter, RemoteTransition remoteTransition) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter,
                    remoteTransition);
        } else {
            return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter);
        }

    }
}
