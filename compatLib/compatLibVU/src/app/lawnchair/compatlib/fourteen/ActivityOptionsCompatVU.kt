package app.lawnchair.compatlib.fourteen;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;
import android.window.RemoteTransition;

import app.lawnchair.compatlib.ActivityOptionsCompat;

class ActivityOptionsCompatVU : ActivityOptionsCompat() {
    override fun makeCustomAnimation(
        context: Context,
        enterResId: Int,
        exitResId: Int,
        callback: Runnable?,
        callbackHandler: Handler?
    ): ActivityOptions {
       TODO()
    }

    override fun makeRemoteAnimation(
        remoteAnimationAdapter: RemoteAnimationAdapter,
        remoteTransition: Any,
        debugName: String
    ): ActivityOptions {
        return ActivityOptions.makeRemoteAnimation(
            remoteAnimationAdapter,
            remoteTransition as RemoteTransition
        )
    }
}
