package app.lawnchair.compatlib;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import android.view.RemoteAnimationAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface ActivityOptionsCompat {

    @NonNull
    ActivityOptions makeCustomAnimation(
            @NonNull Context context,
            int enterResId,
            int exitResId,
            @NonNull final Handler callbackHandler,
            @Nullable final Runnable startedListener,
            @Nullable final Runnable finishedListener);

    @NonNull
    ActivityOptions makeRemoteAnimation(
            @Nullable RemoteAnimationAdapter remoteAnimationAdapter,
            @Nullable Object remoteTransition,
            @Nullable String debugName);

    /**
     * Public-SDK fallback when hidden {@link ActivityOptions} animation overloads are missing or
     * blocked by OEM/Android non-SDK API restrictions.
     */
    @NonNull
    static ActivityOptions makeCustomAnimationFallback(
            @NonNull Context context,
            int enterResId,
            int exitResId,
            @NonNull final Handler callbackHandler,
            @Nullable final Runnable startedListener,
            @Nullable final Runnable finishedListener) {
        if (startedListener != null) {
            callbackHandler.post(startedListener);
        }
        if (finishedListener != null) {
            callbackHandler.post(finishedListener);
        }
        if (enterResId == 0 && exitResId == 0) {
            return ActivityOptions.makeBasic();
        }
        return ActivityOptions.makeCustomAnimation(context, enterResId, exitResId);
    }
}
