package app.lawnchair.compatlib.thirteen;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.RemoteAnimationAdapter;
import android.window.RemoteTransition;

import app.lawnchair.compatlib.twelve.ActivityOptionsCompatVS;

class ActivityOptionsCompatVT : ActivityOptionsCompatVS() {

    companion object {
        private const val TAG = "ActivityOptionsCompatVT"
    }

    override fun makeRemoteAnimation(
        remoteAnimationAdapter: RemoteAnimationAdapter,
        remoteTransition: Any,
        debugName: String
    ): ActivityOptions {
        Log.e(TAG, "makeRemoteAnimation: $debugName")
        return ActivityOptions.makeRemoteAnimation(remoteAnimationAdapter, remoteTransition as RemoteTransition)
    }
}
