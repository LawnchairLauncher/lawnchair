package app.lawnchair.compatlib;

import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.TransitionInfo;

public interface RemoteTransitionStub {
    /**
     * Starts a transition animation. Once complete, the implementation should call
     * `finishCallback`.
     *
     * @param token An identifier for the transition that should be animated.
     */
    void startAnimation(IBinder token, TransitionInfo info, SurfaceControl.Transaction t,
                        IRemoteTransitionFinishedCallback finishCallback);

    /**
     * Attempts to merge a transition animation into the animation that is currently
     * being played by this remote. If merge is not possible/supported, this should be a no-op.
     * If it *is* merged, the implementation should call `finishCallback` immediately.
     *
     * @param transition An identifier for the transition that wants to be merged.
     * @param mergeTarget The transition that is currently being animated by this remote.
     *                    If it can be merged, call `finishCallback`; otherwise, do
     *                    nothing.
     */
    void mergeAnimation(IBinder transition, TransitionInfo info,
                        SurfaceControl.Transaction t, IBinder mergeTarget,
                        IRemoteTransitionFinishedCallback finishCallback);
}
