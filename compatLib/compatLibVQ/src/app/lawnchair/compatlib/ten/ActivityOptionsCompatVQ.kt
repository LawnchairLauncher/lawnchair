package app.lawnchair.compatlib.ten;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;

import app.lawnchair.compatlib.ActivityOptionsCompat;

class ActivityOptionsCompatVQ : ActivityOptionsCompat() {
    override fun makeCustomAnimation(
        context: Context,
        enterResId: Int,
        exitResId: Int,
        callback: Runnable?,
        callbackHandler: Handler?
    ): ActivityOptions {
        return ActivityOptions.makeCustomAnimation(context, enterResId, exitResId,
            callbackHandler,
            ActivityOptions.OnAnimationStartedListener {
                callback.let { callbackHandler?.post(it) }
            })
    }

    override fun makeRemoteAnimation(
        remoteAnimationAdapter: RemoteAnimationAdapter,
        remoteTransition: Any,
        debugName: String
    ): ActivityOptions {
        return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter)
    }
}
