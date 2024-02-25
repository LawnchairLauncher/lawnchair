package app.lawnchair.compatlib.fourteen;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import app.lawnchair.compatlib.thirteen.ActivityOptionsCompatVT;

@RequiresApi(34)
public class ActivityOptionsCompatVU extends ActivityOptionsCompatVT {
    @NonNull
    @Override
    public ActivityOptions makeCustomAnimation(
            @NonNull Context context,
            int enterResId,
            int exitResId,
            @NonNull final Handler callbackHandler,
            @Nullable final Runnable startedListener,
            @Nullable final Runnable finishedListener) {
        return ActivityOptions.makeCustomAnimation(
                context,
                enterResId,
                exitResId,
                0,
                callbackHandler,
                new ActivityOptions.OnAnimationStartedListener() {
                    @Override
                    public void onAnimationStarted(long elapsedRealTime) {
                        if (startedListener != null) {
                            startedListener.run();
                        }
                    }
                },
                new ActivityOptions.OnAnimationFinishedListener() {
                    @Override
                    public void onAnimationFinished(long elapsedRealTime) {
                        if (finishedListener != null) {
                            finishedListener.run();
                        }
                    }
                });
    }
}
