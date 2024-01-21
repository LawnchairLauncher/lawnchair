package app.lawnchair.compatlib;

import android.app.ActivityOptions
import android.content.Context
import android.os.Handler
import android.view.RemoteAnimationAdapter

abstract class ActivityOptionsCompat {

    abstract fun makeCustomAnimation(
        context: Context,
        enterResId: Int,
        exitResId: Int,
        callback: Runnable?,
        callbackHandler: Handler?
    ): ActivityOptions

    abstract fun makeRemoteAnimation(
        remoteAnimationAdapter: RemoteAnimationAdapter,
        remoteTransition: Any,
        debugName: String
    ): ActivityOptions
}
