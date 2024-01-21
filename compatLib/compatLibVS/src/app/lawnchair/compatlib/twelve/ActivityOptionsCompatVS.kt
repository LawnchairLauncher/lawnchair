package app.lawnchair.compatlib.twelve;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.RemoteAnimationAdapter;

import app.lawnchair.compatlib.ActivityOptionsCompat;

open class ActivityOptionsCompatVS : ActivityOptionsCompat() {
    companion object {
        private const val TAG = "ActivityManagerCompatVT"
    }

    override fun makeCustomAnimation(
        context: Context,
        enterResId: Int,
        exitResId: Int,
        callback: Runnable?,
        callbackHandler: Handler?
    ): ActivityOptions {
        return ActivityOptions.makeCustomTaskAnimation(
            context,
            enterResId,
            exitResId,
            callbackHandler,
            ActivityOptions.OnAnimationStartedListener {
                callback?.let {
                    callbackHandler?.post(it)
                }
            },
            null /* finishedListener */
        )
    }

    override fun makeRemoteAnimation(
        remoteAnimationAdapter: RemoteAnimationAdapter,
        remoteTransition: Any,
        debugName: String
    ): ActivityOptions {
        Log.e(TAG, "makeRemoteAnimation: $debugName")
        return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter)
    }
}
